package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.custom.MobHeadBlock;
import com.hasoook.hasoook.block.entity.custom.MobHeadBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * 生物头方块的 BER，渲染 3D 生物头部模型。
 */
public class MobHeadBlockRenderer implements BlockEntityRenderer<MobHeadBlockEntity, MobHeadBlockRenderer.State> {

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    // 持久化缓存：模型部件、包围盒、纹理
    private static final Map<String, ModelPart> HEAD_CLONES = new HashMap<>();
    private static final Map<String, HeadModelInfo> HEAD_INFOS = new HashMap<>();
    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();

    // ==================== Render State ====================

    public static class State extends BlockEntityRenderState {
        public String entityType = "";
        public String playerUuid = "";
        public String playerName = "";
        public String cacheKey = "";
        public int rotation = 0;
        public Direction facing = Direction.UP;
        public int packedLight = 0xF000F0;
    }

    /**
     * 头部几何体信息。pivot 来自原始头部部件（用于世界坐标计算），
     * bounds 是所有 Cube 的总包围盒（局部空间，已累加子部件偏移）。
     * alignMaxY 是排除了鼻子等子部件后的真实头部底部，用于贴合地面。
     */
    private record HeadModelInfo(
            float pivotX, float pivotY, float pivotZ,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float alignMaxY
    ) {}

    private static boolean createStateLogged = false;
    private static boolean extractStateLogged = false;

    @Override
    public State createRenderState() {
        if (!createStateLogged) {
            createStateLogged = true;
            Hasoook.LOGGER.info("[MobHeadBER] createRenderState() called!");
        }
        return new State();
    }

    @Override
    public void extractRenderState(MobHeadBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 worldOffset, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        // ★ 必须先设置基础状态（blockPos 等），否则渲染位置会错
        BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);

        String entityType = blockEntity.getEntityType();
        String playerUuid = blockEntity.getPlayerUuid();
        String playerName = blockEntity.getPlayerName();
        state.entityType = entityType;
        state.playerUuid = playerUuid;
        state.playerName = playerName;
        state.rotation = blockEntity.getBlockState().getValue(MobHeadBlock.ROTATION);
        state.facing = blockEntity.getBlockState().getValue(MobHeadBlock.FACING);

        // ★ 根据方块位置计算真实光照（而非硬编码的 FULL_BRIGHT）
        if (blockEntity.getLevel() != null) {
            state.packedLight = LevelRenderer.getLightColor(
                    blockEntity.getLevel(), blockEntity.getBlockPos());
        }

        boolean isPlayerHead = "minecraft:player".equals(entityType);
        if (entityType.isEmpty()) {
            state.cacheKey = "__fallback__";
        } else if (isPlayerHead && playerUuid != null && !playerUuid.isEmpty()) {
            state.cacheKey = entityType + ":" + playerUuid;
        } else {
            state.cacheKey = entityType;
        }

        if (!extractStateLogged) {
            extractStateLogged = true;
            Hasoook.LOGGER.info("[MobHeadBER] extractRenderState() called! entityType='{}' cacheKey='{}' pos={}",
                    state.entityType, state.cacheKey, state.blockPos);
        }
    }

    private static boolean submitLogged = false; // 一次性调试标记

    @Override
    public void submit(State state, PoseStack ps, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {

        // 一次性日志：确认 BER 的 submit 被调用了
        if (!submitLogged) {
            submitLogged = true;
            Hasoook.LOGGER.info("[MobHeadBER] submit() called! entityType='{}' cacheKey='{}'",
                    state.entityType, state.cacheKey);
        }

        // 服务端数据尚未同步到客户端，跳过本帧，避免闪现史蒂夫头
        if (state.entityType == null || state.entityType.isEmpty()) {
            return;
        }

        // 确保 fallback 已加载（实际生物头加载失败时使用）
        ensureFallbackLoaded();

        String cacheKey = state.cacheKey;
        if (cacheKey == null || cacheKey.isEmpty()) cacheKey = "__fallback__";

        ModelPart headClone = HEAD_CLONES.get(cacheKey);
        HeadModelInfo headInfo = HEAD_INFOS.get(cacheKey);

        // 首次渲染时加载并缓存模型（texture 不在这里缓存，见下方）
        if (headClone == null) {
            loadAndCacheHead(cacheKey, state.entityType, state.playerUuid, state.playerName);
            headClone = HEAD_CLONES.get(cacheKey);
            headInfo = HEAD_INFOS.get(cacheKey);
        }

        // ★ 玩家头纹理：每帧重新获取（不缓存），与物品渲染器行为一致
        //   PlayerInfo.getSkin() 首帧可能返回默认皮肤，后续帧解析完成后返回真实皮肤
        Identifier texture;
        if ("minecraft:player".equals(state.entityType)
                && state.playerUuid != null && !state.playerUuid.isEmpty()) {
            texture = getPlayerSkinTexture(state.playerUuid, state.playerName);
        } else {
            texture = TEXTURE_CACHE.get(cacheKey);
        }

        // 如果模型加载失败，使用 fallback
        if (headClone == null || headInfo == null) {
            Hasoook.LOGGER.warn("[MobHeadBER] primary load failed for '{}', trying fallback", cacheKey);
            headClone = HEAD_CLONES.get("__fallback__");
            headInfo = HEAD_INFOS.get("__fallback__");
            texture = TEXTURE_CACHE.get("__fallback__");
        }

        // 纹理仍为空 → 使用 fallback（玩家头每帧重取，不会永久卡在 fallback）
        if (texture == null) {
            texture = FALLBACK_TEXTURE;
        }

        if (headClone == null || headInfo == null) {
            Hasoook.LOGGER.error("[MobHeadBER] headClone/headInfo is null — cannot render! headClone={} headInfo={}",
                    headClone != null, headInfo != null);
            return;
        }

        Direction facing = state.facing;
        ps.pushPose();

        // ===== 基础定位与旋转 =====
        if (facing == Direction.UP) {
            // --- 地面放置 ---
            ps.translate(0.5F, 0.0F, 0.5F);

            // Y轴旋转（放置朝向）
            // RotX(180)*RotY(θ), 脸 +Z: [-sin(θ), 0, -cos(θ)]
            // r=0→脸北(朝玩家), r=4→脸东 → θ = 180 - r*22.5（顺时针）
            float rotAngle = 180.0F - state.rotation * 22.5F;
            ps.mulPose(Axis.YP.rotationDegrees(rotAngle));

            // X轴旋转
            ps.mulPose(Axis.XP.rotationDegrees(180.0F));
        } else {
            // --- 墙面附着：跟地面一样的旋转逻辑，只改变挂载位置 ---
            // 定位到附着面，往外偏移半个头厚度(4/16)，防止模型嵌进墙里
            float outward = 4.0F / 16.0F;
            float tx = 0.5F, ty = 0.5F, tz = 0.5F;
            switch (facing) {
                case NORTH: tz = 1.0F - outward; break;
                case SOUTH: tz = outward;        break;
                case EAST:  tx = outward;        break;
                case WEST:  tx = 1.0F - outward; break;
            }
            ps.translate(tx, ty, tz);

            // 与地面完全相同的 Y 轴旋转（玩家朝向决定）
            float rotAngle = 180.0F - state.rotation * 22.5F;
            ps.mulPose(Axis.YP.rotationDegrees(rotAngle));

            // X轴翻转（与地面相同）
            ps.mulPose(Axis.XP.rotationDegrees(180.0F));
        }

        // ===== 缩放（超高头自动压缩） =====
        float headHeight = (headInfo.maxY() - headInfo.minY()) / 16f;
        float fitScale = headHeight > 1.0f ? 1.0f / headHeight : 1.0f;
        float scale = 0.95F * fitScale;
        ps.scale(scale, scale, scale);

        // ===== 居中定位 =====
        float cx, cy, cz;
        if (facing == Direction.UP) {
            // 地面：底部对齐，水平居中
            cx = headInfo.pivotX() / 16f;
            cz = (headInfo.pivotZ() + (headInfo.minZ() + headInfo.maxZ()) / 2f) / 16f;
            // 关键修复：使用 alignMaxY 替代 maxY，避免鼻子/胡子导致底部计算错误
            cy = (headInfo.pivotY() + headInfo.alignMaxY()) / 16f;
        } else {
            // 墙面：垂直+水平居中，不做额外的背部贴合
            cx = headInfo.pivotX() / 16f;
            cy = (headInfo.pivotY() + (headInfo.minY() + headInfo.maxY()) / 2f) / 16f;
            cz = (headInfo.pivotZ() + (headInfo.minZ() + headInfo.maxZ()) / 2f) / 16f;
        }
        ps.translate(-cx, -cy, -cz);

        collector.submitModelPart(headClone, ps, RenderTypes.entityCutoutNoCull(texture),
                state.packedLight, OverlayTexture.NO_OVERLAY,
                null, false, false, -1, null, 0);

        ps.popPose();
    }

    /** 确保史蒂夫 fallback 头部已加载 */
    private static void ensureFallbackLoaded() {
        if (HEAD_CLONES.containsKey("__fallback__")) return;
        loadAndCacheHead("__fallback__", "", null, null);
    }

    /**
     * 创建实体并从中提取头部模型、包围盒和纹理，缓存备用。
     */
    private static void loadAndCacheHead(String cacheKey, String entityTypeStr,
                                         @Nullable String playerUuid, @Nullable String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            com.hasoook.hasoook.Hasoook.LOGGER.warn("[MobHeadBER] level is null, cannot load head: {}", cacheKey);
            return;
        }

        boolean isPlayerHead = "minecraft:player".equals(entityTypeStr);
        Identifier typeId = entityTypeStr.isEmpty() ? null : Identifier.tryParse(entityTypeStr);
        EntityType<?> entityType = typeId != null ? BuiltInRegistries.ENTITY_TYPE.getValue(typeId) : null;

        Entity entity = null;
        try {
            if (entityType == null || isPlayerHead) {
                entity = EntityType.ZOMBIE.create(mc.level, EntitySpawnReason.LOAD);
            } else {
                entity = entityType.create(mc.level, EntitySpawnReason.LOAD);
            }
            if (entity == null) {
                com.hasoook.hasoook.Hasoook.LOGGER.warn("[MobHeadBER] entity creation returned null for: {}", cacheKey);
                return;
            }

            freezeEntityPose(entity);
            EntityRenderer<? super Entity, ?> renderer =
                    mc.getEntityRenderDispatcher().getRenderer(entity);
            ModelPart headPart = findHeadFromRenderer(renderer);
            if (headPart == null) {
                com.hasoook.hasoook.Hasoook.LOGGER.warn("[MobHeadBER] findHeadFromRenderer returned null for: {}", cacheKey);
                return;
            }

            ModelPart clone = deepClonePart(headPart);
            HEAD_CLONES.put(cacheKey, clone);
            HEAD_INFOS.put(cacheKey, computeHeadInfo(headPart));

            Identifier tex = null;
            if (isPlayerHead) {
                // ★ 玩家头纹理不缓存——每帧通过 PlayerInfo.getSkin() 重新获取。
                //    因为 PlayerInfo 的 skinLookup supplier 首帧可能返回默认皮肤，
                //    缓存会导致后续帧无法获取到已解析的真实皮肤。
                tex = getPlayerSkinTexture(playerUuid, playerName);
            } else if (entityType != null) {
                tex = getEntityTexture(renderer, entity);
                TEXTURE_CACHE.put(cacheKey, tex != null ? tex : FALLBACK_TEXTURE);
            }

            com.hasoook.hasoook.Hasoook.LOGGER.info("[MobHeadBER] loaded head: {} (isPlayer={} texture={})",
                    cacheKey, isPlayerHead, tex);
        } catch (Exception e) {
            com.hasoook.hasoook.Hasoook.LOGGER.error("[MobHeadBER] failed to load head: {}", cacheKey, e);
        } finally {
            if (entity != null) entity.discard();
        }
    }

    // ==================== Head Model Utilities ====================

    private static void freezeEntityPose(Entity entity) {
        entity.setYRot(0);
        entity.setXRot(0);
        entity.yRotO = 0;
        entity.xRotO = 0;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0;
            living.yHeadRotO = 0;
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
        }
    }

    @Nullable
    private static ModelPart findHeadFromRenderer(EntityRenderer<?, ?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> lr)) return null;
        return findHead(lr.getModel().root());
    }

    @Nullable
    private static ModelPart findHead(ModelPart part) {
        ModelPart found = findHeadStrict(part);
        if (found != null) return found;
        return part;
    }

    @Nullable
    private static ModelPart findHeadStrict(ModelPart part) {
        if (part.hasChild("head")) return part.getChild("head");
        for (String name : new String[]{"center_head", "right_head", "left_head"}) {
            if (part.hasChild(name)) return part.getChild(name);
        }
        if (part.hasChild("head_parts")) return part.getChild("head_parts");
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            if (entry.getKey().toLowerCase().contains("head")) continue;
            ModelPart found = findHeadStrict(entry.getValue());
            if (found != null) return found;
        }
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.equals("head_parts") && name.contains("head")) {
                ModelPart inner = findHeadStrict(entry.getValue());
                if (inner != null) return inner;
                return entry.getValue();
            }
            ModelPart found = findHeadStrict(entry.getValue());
            if (found != null) return found;
        }
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            if (entry.getKey().toLowerCase().equals("body")) return entry.getValue();
        }
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (name.contains("head")) continue;
            if (entry.getValue().hasChild("body")) return entry.getValue().getChild("body");
        }
        return null;
    }

    /** 计算头部克隆体中所有 Cube 的总包围盒（并集），用于居中定位 */
    @SuppressWarnings("unchecked")
    private static HeadModelInfo computeHeadInfo(ModelPart headPart) {
        List<CubeWithOffset> allCubes = new ArrayList<>();
        collectAllCubesWithOffset(headPart, 0, 0, 0, allCubes);

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        int cubeCount = 0, failCount = 0;

        for (CubeWithOffset cwo : allCubes) {
            float[] localBounds = computeCubeBounds(cwo.cube);
            if (localBounds == null) {
                failCount++;
                continue;
            }
            cubeCount++;
            float cx = localBounds[0] + cwo.offX;
            float cy = localBounds[1] + cwo.offY;
            float cz = localBounds[2] + cwo.offZ;
            float cMx = localBounds[3] + cwo.offX;
            float cMy = localBounds[4] + cwo.offY;
            float cMz = localBounds[5] + cwo.offZ;
            if (cx < minX) minX = cx;
            if (cy < minY) minY = cy;
            if (cz < minZ) minZ = cz;
            if (cMx > maxX) maxX = cMx;
            if (cMy > maxY) maxY = cMy;
            if (cMz > maxZ) maxZ = cMz;
        }

        // ================= 核心修复逻辑 =================
        // 计算真正的底部（过滤掉村民鼻子、挂饰等子部件的干扰）
        float coreMaxY = -Float.MAX_VALUE;
        for (ModelPart.Cube cube : (List<ModelPart.Cube>) headPart.cubes) {
            float[] localBounds = computeCubeBounds(cube);
            if (localBounds != null) {
                if (localBounds[4] > coreMaxY) {
                    coreMaxY = localBounds[4];
                }
            }
        }
        // 如果根节点存在实体 cube，优先使用它的 maxY 作为对齐基准；否则回退到全局 maxY
        float alignMaxY = (coreMaxY != -Float.MAX_VALUE) ? coreMaxY : maxY;
        // ===============================================

        // 无 Cube 时回退到标准 8×8×8 头部
        if (minX == Float.MAX_VALUE) {
            Hasoook.LOGGER.warn("[MobHeadBER] computeHeadInfo: all {} cubes failed bounds! Using default.", failCount);
            return new HeadModelInfo(0, 0, 0, -4, -4, -4, 4, 4, 4, 4);
        }

        float pvX = headPart.getInitialPose().x();
        float pvY = headPart.getInitialPose().y();
        float pvZ = headPart.getInitialPose().z();

        HeadModelInfo info = new HeadModelInfo(pvX, pvY, pvZ, minX, minY, minZ, maxX, maxY, maxZ, alignMaxY);
        Hasoook.LOGGER.info("[MobHeadBER] head bounds: X[{} {}] Y[{} {}] Z[{} {}] alignY={} pivotX={} pivotZ={} cubes={}/{} failed={}",
                String.format("%.1f", minX), String.format("%.1f", maxX),
                String.format("%.1f", minY), String.format("%.1f", maxY),
                String.format("%.1f", minZ), String.format("%.1f", maxZ),
                String.format("%.1f", alignMaxY),
                String.format("%.1f", pvX), String.format("%.1f", pvZ),
                cubeCount, allCubes.size(), failCount);
        return info;
    }

    /** 带累计偏移的 Cube，偏移量是子部件在头部根空间的 pivot 位置 */
    private record CubeWithOffset(ModelPart.Cube cube, float offX, float offY, float offZ) {}

    @SuppressWarnings("unchecked")
    private static void collectAllCubesWithOffset(ModelPart part, float offX, float offY, float offZ,
                                                  List<CubeWithOffset> list) {
        for (ModelPart.Cube cube : (List<ModelPart.Cube>) part.cubes) {
            list.add(new CubeWithOffset(cube, offX, offY, offZ));
        }
        for (ModelPart child : ((Map<String, ModelPart>) part.children).values()) {
            collectAllCubesWithOffset(child,
                    offX + child.initialPose.x(),
                    offY + child.initialPose.y(),
                    offZ + child.initialPose.z(),
                    list);
        }
    }

    @Nullable
    private static float[] computeCubeBounds(ModelPart.Cube cube) {
        // 优先用多边形顶点（绝对坐标，不受 origin/dims 内部约定影响）
        float[] polyBounds = tryPolygonVertices(cube);
        if (polyBounds != null) return polyBounds;
        // 回退到反射字段方式
        return tryOriginDimensionFields(cube);
    }

    @Nullable
    private static float[] tryOriginDimensionFields(ModelPart.Cube cube) {
        try {
            Class<?> clazz = cube.getClass();
            Float ox = null, oy = null, oz = null;
            Float dx = null, dy = null, dz = null;
            Float gx = null, gy = null, gz = null;
            boolean hasMaxField = false;
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                if (f.getType() != float.class) continue;
                f.setAccessible(true);
                String name = f.getName().toLowerCase();
                float val = f.getFloat(cube);
                if (matchesDim(name, "x") && (isOrigin(name) || isMin(name))) ox = val;
                if (matchesDim(name, "y") && (isOrigin(name) || isMin(name))) oy = val;
                if (matchesDim(name, "z") && (isOrigin(name) || isMin(name))) oz = val;
                if (matchesDim(name, "x") && (isDimension(name) || isSize(name))) dx = val;
                if (matchesDim(name, "y") && (isDimension(name) || isSize(name))) dy = val;
                if (matchesDim(name, "z") && (isDimension(name) || isSize(name))) dz = val;
                if (matchesDim(name, "x") && isMax(name)) { dx = val; hasMaxField = true; }
                if (matchesDim(name, "y") && isMax(name)) { dy = val; hasMaxField = true; }
                if (matchesDim(name, "z") && isMax(name)) { dz = val; hasMaxField = true; }
                if (matchesDim(name, "x") && isGrow(name)) gx = val;
                if (matchesDim(name, "y") && isGrow(name)) gy = val;
                if (matchesDim(name, "z") && isGrow(name)) gz = val;
            }
            if (ox == null || oy == null || oz == null || dx == null || dy == null || dz == null)
                return null;
            if (hasMaxField) { dx = dx - ox; dy = dy - oy; dz = dz - oz; }
            float gxVal = gx != null ? gx : 0f;
            float gyVal = gy != null ? gy : 0f;
            float gzVal = gz != null ? gz : 0f;
            return new float[]{ox - gxVal, oy - gyVal, oz - gzVal,
                    ox + dx + gxVal, oy + dy + gyVal, oz + dz + gzVal};
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static float[] tryPolygonVertices(ModelPart.Cube cube) {
        try {
            Class<?> cubeClass = cube.getClass();
            // 尝试多种可能的多边形数组字段名
            Object[] polygons = readArrayField(cube, cubeClass,
                    "polygons", "quads", "faces", "elements", "meshes");
            if (polygons == null || polygons.length == 0) return null;

            float[] bounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean foundAny = false;

            for (Object poly : polygons) {
                if (poly == null) continue;
                // 尝试多种顶点数组字段名
                Object[] vertices = readArrayField(poly, poly.getClass(),
                        "vertices", "corners", "points", "vertexes");
                if (vertices == null) continue;
                for (Object vert : vertices) {
                    if (vert == null) continue;
                    // 尝试直接就是 Vector3fc（某些 record 实现）
                    if (vert instanceof org.joml.Vector3fc v) {
                        accumulateBounds(bounds, v);
                        foundAny = true;
                        continue;
                    }
                    // 尝试 pos/position 字段
                    Object pos = readField(vert, vert.getClass(),
                            "pos", "position", "xyz", "location", "coords");
                    if (pos instanceof org.joml.Vector3fc v2) {
                        accumulateBounds(bounds, v2);
                        foundAny = true;
                    }
                }
            }
            if (!foundAny) return null;
            return bounds;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void accumulateBounds(float[] b, org.joml.Vector3fc v) {
        if (v.x() < b[0]) b[0] = v.x();
        if (v.y() < b[1]) b[1] = v.y();
        if (v.z() < b[2]) b[2] = v.z();
        if (v.x() > b[3]) b[3] = v.x();
        if (v.y() > b[4]) b[4] = v.y();
        if (v.z() > b[5]) b[5] = v.z();
    }

    @Nullable
    private static Object readField(Object obj, Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Nullable
    private static Object[] readArrayField(Object obj, Class<?> clazz, String... names) {
        Object val = readField(obj, clazz, names);
        if (val instanceof Object[] arr) return arr;
        if (val instanceof List<?> list) return list.toArray();
        return null;
    }

    private static boolean matchesDim(String n, String a) {
        return n.contains(a) && !n.contains("xr") && !n.contains("yr") && !n.contains("zr");
    }
    private static boolean isOrigin(String n) { return n.contains("origin"); }
    private static boolean isMin(String n) { return n.contains("min") || n.contains("from"); }
    private static boolean isMax(String n) { return n.contains("max") || n.contains("to"); }
    private static boolean isDimension(String n) { return n.contains("dimension"); }
    private static boolean isSize(String n) { return n.contains("size"); }
    private static boolean isGrow(String n) { return n.contains("grow") || n.contains("inflate") || n.contains("expand"); }

    /**
     * 深拷贝头部（保留全部 pose，与原始行为一致）。
     */
    @SuppressWarnings("unchecked")
    private static ModelPart deepClonePart(ModelPart source) {
        List<ModelPart.Cube> sourceCubes = (List<ModelPart.Cube>) source.cubes;
        Map<String, ModelPart> sourceChildren = (Map<String, ModelPart>) source.children;
        Map<String, ModelPart> clonedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, ModelPart> entry : sourceChildren.entrySet()) {
            clonedChildren.put(entry.getKey(), deepClonePart(entry.getValue()));
        }
        ModelPart clone = new ModelPart(sourceCubes, clonedChildren);
        clone.setInitialPose(source.initialPose);
        clone.loadPose(source.initialPose);
        return clone;
    }

    /**
     * 根据玩家 UUID 和名称获取皮肤纹理（同步，优先从玩家列表获取已解析的皮肤）。
     * <p>
     * 关键：PlayerInfo 持有完整的 GameProfile（含纹理属性），内部使用
     * {@code SkinManager.createLookup()}，该方法在皮肤解析完成后持久返回真实纹理。
     * 对于本地玩家，皮肤在第一帧渲染时已解析完毕，因此调用立即返回真实皮肤。
     */
    private static Identifier getPlayerSkinTexture(@Nullable String uuidString, @Nullable String playerName) {
        if (uuidString == null || uuidString.isEmpty()) return FALLBACK_TEXTURE;
        try {
            UUID uuid = UUID.fromString(uuidString);
            Minecraft mc = Minecraft.getInstance();

            // ★ 方案1：通过 PlayerInfo 获取（在线玩家，含完整 GameProfile + 纹理属性）
            var connection = mc.getConnection();
            if (connection != null) {
                PlayerInfo playerInfo = connection.getPlayerInfo(uuid);
                if (playerInfo != null) {
                    return playerInfo.getSkin().body().texturePath();
                }
            }

            // ★ 方案2：回退到 SkinManager（用于没有 PlayerInfo 的离线场景）
            String name = (playerName != null && !playerName.isEmpty()) ? playerName : "";
            GameProfile profile = new GameProfile(uuid, name);
            var skinFuture = mc.getSkinManager().get(profile);
            var optSkin = skinFuture.getNow(null);
            if (optSkin != null && optSkin.isPresent()) {
                return optSkin.get().body().texturePath();
            }
        } catch (Exception ignored) {}
        return FALLBACK_TEXTURE;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Identifier getEntityTexture(EntityRenderer<?, ?> renderer, Entity entity) {
        if (renderer instanceof LivingEntityRenderer lr) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            var renderState = dispatcher.extractEntity(entity, 0.0F);
            if (renderState instanceof LivingEntityRenderState livingState) {
                return lr.getTextureLocation(livingState);
            }
        }
        return null;
    }
}
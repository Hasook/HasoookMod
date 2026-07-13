package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.custom.MobHeadItem;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 生物头物品的 3D 渲染器。
 * 采用统一固定缩放，并利用”最大体积法”寻找主头颅进行完美居中，
 * 彻底消除村民鼻子、女巫帽子等不对称特征导致的 GUI/手持偏移问题。
 */
public class MobHeadSpecialRenderer implements SpecialModelRenderer<MobHeadSpecialRenderer.HeadArg> {

    /** 头部渲染参数：实体类型 + 可选的玩家 UUID 和名称（用于皮肤查询） */
    public record HeadArg(@Nullable EntityType<?> entityType, @Nullable String playerUuid,
                          @Nullable String playerName) {}

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    private static final float BASE_SCALE = 1.0F;

    // --- 第一人称手持偏移 ---
    private static final float FP_TRANSLATE_X = 0.45F;
    private static final float FP_TRANSLATE_Y = 0.5F;
    private static final float FP_TRANSLATE_Z = 0.4F;
    private static final float FP_ROTATE_Y_RIGHT = 180.0F;
    private static final float FP_ROTATE_Y_LEFT = 180.0F;

    // --- 第三人称手持偏移 ---
    private static final float TP_TRANSLATE_X = 0.4F;
    private static final float TP_TRANSLATE_Y = 0.3F;
    private static final float TP_TRANSLATE_Z = 0.5F;
    private static final float TP_ROTATE_Y = 90.0F;
    private static final float TP_ROTATE_Z = 180.0F;

    private static final Map<String, ModelPart> HEAD_CLONES = new HashMap<>();
    private static final Map<String, HeadModelInfo> HEAD_INFOS = new HashMap<>();

    private record HeadModelInfo(
            float pivotX, float pivotY, float pivotZ,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ
    ) {
        float cubeCenterX() { return (minX + maxX) / 2f; }
        float cubeCenterY() { return (minY + maxY) / 2f; }
        float cubeCenterZ() { return (minZ + maxZ) / 2f; }

        float worldCenterX() { return pivotX + cubeCenterX(); }
        float worldCenterY() { return pivotY + cubeCenterY(); }
        float worldCenterZ() { return pivotZ + cubeCenterZ(); }
    }

    private static volatile boolean cubeFieldsLogged = false;

    private static HeadModelInfo computeHeadInfo(ModelPart headPart) {
        org.joml.Vector3f pivot = new org.joml.Vector3f(
                headPart.getInitialPose().x(),
                headPart.getInitialPose().y(),
                headPart.getInitialPose().z()
        );

        // 默认标准头颅尺寸
        float[] bestBounds = new float[] { -4, -4, -4, 4, 4, 4 };
        float maxVolume = -1f;

        // 1. 收集头部节点下的所有独立 Cube（包括子节点里的，比如帽子）
        List<ModelPart.Cube> allCubes = new ArrayList<>();
        collectAllCubes(headPart, allCubes);

        // 2. 核心逻辑：最大体积法 (寻找真正的“头盖骨”来做居中基准)
        for (ModelPart.Cube cube : allCubes) {
            float[] bounds = new float[] {
                    Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE
            };

            collectCubeBounds(cube, bounds);

            if (bounds[0] != Float.MAX_VALUE) {
                float dx = bounds[3] - bounds[0];
                float dy = bounds[4] - bounds[1];
                float dz = bounds[5] - bounds[2];
                float volume = dx * dy * dz;

                // 谁体积大，谁就是居中的唯一标准
                if (volume > maxVolume) {
                    maxVolume = volume;
                    bestBounds = bounds;
                }
            }
        }

        return new HeadModelInfo(
                pivot.x(), pivot.y(), pivot.z(),
                bestBounds[0], bestBounds[1], bestBounds[2],
                bestBounds[3], bestBounds[4], bestBounds[5]
        );
    }

    @SuppressWarnings("unchecked")
    private static void collectAllCubes(ModelPart part, List<ModelPart.Cube> list) {
        list.addAll((List<ModelPart.Cube>) part.cubes);
        for (ModelPart child : ((Map<String, ModelPart>) part.children).values()) {
            collectAllCubes(child, list);
        }
    }

    private static void collectCubeBounds(ModelPart.Cube cube, float[] bounds) {
        if (tryOriginFields(cube, bounds)) return;
        tryPolygonVertices(cube, bounds);
    }

    private static boolean tryOriginFields(ModelPart.Cube cube, float[] bounds) {
        try {
            Class<?> clazz = cube.getClass();
            if (!cubeFieldsLogged) {
                cubeFieldsLogged = true;
                StringBuilder sb = new StringBuilder("[MobHead] Cube fields: ");
                for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                    sb.append(f.getName()).append("(").append(f.getType().getSimpleName()).append(") ");
                }
                Hasoook.LOGGER.info(sb.toString());
            }

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
                return false;

            if (hasMaxField) {
                dx = dx - ox; dy = dy - oy; dz = dz - oz;
            }

            float gxVal = gx != null ? gx : 0f;
            float gyVal = gy != null ? gy : 0f;
            float gzVal = gz != null ? gz : 0f;

            updateBounds(bounds,
                    ox - gxVal, oy - gyVal, oz - gzVal,
                    ox + dx + gxVal, oy + dy + gyVal, oz + dz + gzVal);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void tryPolygonVertices(ModelPart.Cube cube, float[] bounds) {
        try {
            Class<?> cubeClass = cube.getClass();
            Object[] polygons = readArrayField(cube, cubeClass, "polygons", "quads");
            if (polygons == null) return;

            for (Object poly : polygons) {
                if (poly == null) continue;
                Object[] vertices = readArrayField(poly, poly.getClass(), "vertices");
                if (vertices == null) continue;

                for (Object vert : vertices) {
                    if (vert == null) continue;
                    Object pos = readField(vert, vert.getClass(), "pos", "position");
                    if (pos instanceof org.joml.Vector3fc v) {
                        updateBounds(bounds, v.x(), v.y(), v.z(), v.x(), v.y(), v.z());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    private static Object readField(Object obj, Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Nullable
    private static Object[] readArrayField(Object obj, Class<?> clazz, String... names) {
        Object val = readField(obj, clazz, names);
        if (val instanceof Object[] arr) return arr;
        if (val instanceof java.util.List<?> list) return list.toArray();
        return null;
    }

    private static void updateBounds(float[] b, float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ) {
        if (minX < b[0]) b[0] = minX;
        if (minY < b[1]) b[1] = minY;
        if (minZ < b[2]) b[2] = minZ;
        if (maxX > b[3]) b[3] = maxX;
        if (maxY > b[4]) b[4] = maxY;
        if (maxZ > b[5]) b[5] = maxZ;
    }

    private static boolean matchesDim(String fieldName, String axis) {
        return fieldName.contains(axis) && !fieldName.contains("xr") && !fieldName.contains("yr") && !fieldName.contains("zr");
    }
    private static boolean isOrigin(String name) { return name.contains("origin"); }
    private static boolean isMin(String name) { return name.contains("min") || name.contains("from"); }
    private static boolean isMax(String name) { return name.contains("max") || name.contains("to"); }
    private static boolean isDimension(String name) { return name.contains("dimension"); }
    private static boolean isSize(String name) { return name.contains("size"); }
    private static boolean isGrow(String name) { return name.contains("grow") || name.contains("inflate") || name.contains("expand"); }

    @Override
    public @Nullable HeadArg extractArgument(@NonNull ItemStack stack) {
        EntityType<?> type = MobHeadItem.getEntityType(stack);
        String uuid = MobHeadItem.getHeadOwnerUuid(stack);
        String name = MobHeadItem.getHeadOwnerName(stack);
        return new HeadArg(type, uuid, name);
    }

    @Override
    public void submit(
            @Nullable HeadArg arg,
            @NonNull ItemDisplayContext ctx,
            @NonNull PoseStack ps,
            @NonNull SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean foil,
            int outline) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        EntityType<?> entityType = arg != null ? arg.entityType() : null;
        String playerUuid = arg != null ? arg.playerUuid() : null;
        String playerName = arg != null ? arg.playerName() : null;
        boolean isPlayerHead = entityType == EntityType.PLAYER;

        if (entityType == null) {
            renderFallbackHead(collector, ps, ctx, light, overlay, foil, outline);
            return;
        }

        // 玩家实体无法直接创建，用僵尸代理
        Entity entity;
        if (isPlayerHead) {
            entity = EntityType.ZOMBIE.create(mc.level, EntitySpawnReason.LOAD);
        } else {
            entity = entityType.create(mc.level, EntitySpawnReason.LOAD);
        }
        if (entity == null) return;

        try {
            freezeEntityPose(entity);

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderer<? super Entity, ?> renderer = dispatcher.getRenderer(entity);

            ModelPart sharedHead = findHeadPart(renderer);
            EntityRenderState renderState = dispatcher.extractEntity(entity, 0.0F);

            ps.pushPose();

            if (sharedHead != null) {
                String key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();

                HeadModelInfo info = HEAD_INFOS.computeIfAbsent(key, k -> computeHeadInfo(sharedHead));

                // 玩家头 → 从皮肤管理器获取纹理；普通生物 → 从实体渲染器获取
                Identifier texture;
                if (isPlayerHead) {
                    texture = getPlayerSkinTexture(playerUuid, playerName);
                } else {
                    texture = getEntityTexture(renderer, renderState);
                }
                if (texture == null) texture = FALLBACK_TEXTURE;

                RenderType rt = RenderTypes.entityCutoutNoCull(texture);
                int useLight = isStaticContext(ctx) ? LightTexture.FULL_BRIGHT : light;

                ModelPart headCopy = HEAD_CLONES.computeIfAbsent(key, k -> deepClonePart(sharedHead));

                applyHeadTransforms(ps, ctx, info);

                collector.submitModelPart(headCopy, ps, rt, useLight, overlay,
                        null, false, foil, -1, null, outline);
            } else {
                applyFallbackTransforms(ps, ctx, entity);
                net.minecraft.client.renderer.state.CameraRenderState cs = new net.minecraft.client.renderer.state.CameraRenderState();
                dispatcher.submit(renderState, cs, 0.0, 0.0, 0.0, ps, collector);
            }

            ps.popPose();
        } catch (Exception ignored) {
        } finally {
            entity.discard();
        }
    }

    private void renderFallbackHead(SubmitNodeCollector collector, PoseStack ps, ItemDisplayContext ctx,
                                    int light, int overlay, boolean foil, int outline) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 使用 Zombie 替代 Player：僵尸和史蒂夫共享相同的头部模型，且可以在客户端直接创建
        Entity zombie = EntityType.ZOMBIE.create(mc.level, EntitySpawnReason.LOAD);
        if (zombie == null) return;

        try {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderer<? super Entity, ?> renderer = dispatcher.getRenderer(zombie);
            ModelPart sharedHead = findHeadPart(renderer);
            if (sharedHead == null) return;

            ps.pushPose();

            HeadModelInfo info = HEAD_INFOS.computeIfAbsent("__fallback__", k -> computeHeadInfo(sharedHead));
            // 使用史蒂夫贴图，而非僵尸贴图
            RenderType rt = RenderTypes.entityCutoutNoCull(FALLBACK_TEXTURE);
            int useLight = isStaticContext(ctx) ? LightTexture.FULL_BRIGHT : light;
            ModelPart headCopy = HEAD_CLONES.computeIfAbsent("__fallback__", k -> deepClonePart(sharedHead));

            applyHeadTransforms(ps, ctx, info);

            collector.submitModelPart(headCopy, ps, rt, useLight, overlay, null, false, foil, -1, null, outline);

            ps.popPose();
        } finally {
            zombie.discard();
        }
    }

    private void freezeEntityPose(Entity entity) {
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
    private ModelPart findHeadPart(EntityRenderer<?, ?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> lr)) return null;
        return findHead(lr.getModel().root());
    }

    /**
     * 在模型中寻找代表"头部"的 ModelPart（用于物品栏/GUI 渲染）。
     * <p>
     * 优先级：head → 凋灵头 → head_parts 容器（含脖子/耳朵）→ 深层递归 → body 兜底 → 整个模型
     */
    @Nullable
    private ModelPart findHead(ModelPart part) {
        ModelPart found = findHeadStrict(part);
        if (found != null) return found;
        // ★ 最终兜底：连 body 也没有 → 返回整个模型根部件（蠹虫等小生物）
        return part;
    }

    /** 严格递归搜索 head/body，不使用 root 兜底 */
    @Nullable
    private ModelPart findHeadStrict(ModelPart part) {
        // 1. 标准直接匹配: "head"
        if (part.hasChild("head")) return part.getChild("head");
        // 2. 凋灵的三个头
        for (String name : new String[]{"center_head", "right_head", "left_head"}) {
            if (part.hasChild(name)) return part.getChild(name);
        }
        // 3. 马/驴/骡: "head_parts" 容器 → 返回整个容器（包含 head + neck + ears）
        if (part.hasChild("head_parts")) {
            return part.getChild("head_parts");
        }
        // 4. 递归搜索非 head 子部件（如 bone→body→head 等深层嵌套）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            if (entry.getKey().toLowerCase().contains("head")) continue;
            ModelPart found = findHeadStrict(entry.getValue());
            if (found != null) return found;
        }
        // 5. 在 head 命名的子部件中深入搜索
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
        // 6. 搜索 "body"（无头生物兜底，如鱿鱼、恶魂）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            if (entry.getKey().toLowerCase().equals("body")) {
                return entry.getValue();
            }
        }
        // 7. 搜索容器内的 "body"（如 root→bone→body 的蜜蜂）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (name.contains("head")) continue;
            if (entry.getValue().hasChild("body")) {
                return entry.getValue().getChild("body");
            }
        }
        return null;
    }

    /**
     * 根据玩家 UUID 和名称获取皮肤纹理（同步，优先从玩家列表获取已解析的皮肤）。
     * <p>
     * 关键：PlayerInfo 持有完整的 GameProfile（含纹理属性），内部使用
     * {@code SkinManager.createLookup()}，该方法在皮肤解析完成后持久返回真实纹理。
     * 对于本地玩家，皮肤在第一帧渲染时已解析完毕，因此调用立即返回真实皮肤。
     */
    @Nullable
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

    private Identifier getEntityTexture(EntityRenderer<?, ?> renderer, EntityRenderState renderState) {
        if (renderer instanceof LivingEntityRenderer<?, ?, ?> lr) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Identifier tex = ((LivingEntityRenderer) lr).getTextureLocation((LivingEntityRenderState) renderState);
            return tex;
        }
        return null;
    }

    private boolean isStaticContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.GROUND
                || ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.HEAD;
    }

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

    private void applyHeadTransforms(PoseStack ps, ItemDisplayContext ctx, HeadModelInfo info) {
        switch (ctx) {
            case GUI -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.mulPose(Axis.XP.rotationDegrees(30.0F));
                ps.mulPose(Axis.YP.rotationDegrees(135.0F));
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case GROUND -> {
                ps.translate(0.5F, 0.25F, 0.5F);
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case FIXED -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                ps.translate(FP_TRANSLATE_X, FP_TRANSLATE_Y, FP_TRANSLATE_Z);
                float yaw = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? FP_ROTATE_Y_RIGHT : FP_ROTATE_Y_LEFT;
                ps.mulPose(Axis.YP.rotationDegrees(yaw));
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                ps.translate(TP_TRANSLATE_X, TP_TRANSLATE_Y, TP_TRANSLATE_Z);
                ps.mulPose(Axis.YP.rotationDegrees(TP_ROTATE_Y));
                ps.mulPose(Axis.ZP.rotationDegrees(TP_ROTATE_Z));
            }
            case HEAD -> {
                ps.translate(0.5F, -0.25F, 1.0F);

                ps.mulPose(Axis.XP.rotationDegrees(0.0F));
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));

                float headScale = 1.6F;
                ps.scale(headScale, headScale, headScale);
            }
            default -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
        }

        ps.scale(BASE_SCALE, BASE_SCALE, BASE_SCALE);

        // 现在这个中心点是纯粹根据“最大体积块（真正的头盖骨）”计算出的中心，不会被女巫帽子拉偏了
        float cx = info.worldCenterX() / 16f;
        float cy = info.worldCenterY() / 16f;
        float cz = info.worldCenterZ() / 16f;
        ps.translate(-cx, -cy, -cz);
    }

    private void applyFallbackTransforms(PoseStack ps, ItemDisplayContext ctx, Entity e) {
        float bbHeight = e.getBbHeight();
        float fixedScale = 0.5F;

        switch (ctx) {
            case GUI -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.scale(fixedScale, fixedScale, fixedScale);
                ps.mulPose(Axis.XP.rotationDegrees(-15.0F));
                ps.mulPose(Axis.YP.rotationDegrees(135.0F));
            }
            case GROUND -> {
                ps.translate(0.5F, 0.25F, 0.5F);
                ps.scale(fixedScale, fixedScale, fixedScale);
            }
            case FIXED -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.scale(fixedScale, fixedScale, fixedScale);
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                ps.translate(FP_TRANSLATE_X, FP_TRANSLATE_Y, FP_TRANSLATE_Z);
                ps.scale(fixedScale, fixedScale, fixedScale);
                float yaw = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? 10.0F : -10.0F;
                ps.mulPose(Axis.YP.rotationDegrees(yaw));
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                ps.translate(TP_TRANSLATE_X, TP_TRANSLATE_Y, TP_TRANSLATE_Z);
                ps.scale(fixedScale, fixedScale, fixedScale);
            }
            case HEAD -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.scale(fixedScale, fixedScale, fixedScale);
            }
            default -> {
                ps.translate(0.5F, 0.5F, 0.5F);
                ps.scale(fixedScale, fixedScale, fixedScale);
            }
        }
        ps.translate(0.0F, -bbHeight / 2.0F, 0.0F);
    }

    @Override
    public void getExtents(@NonNull Consumer<Vector3fc> consumer) {
        consumer.accept(new org.joml.Vector3f(-0.35F, -0.35F, -0.35F));
        consumer.accept(new org.joml.Vector3f(0.35F, 0.35F, 0.35F));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<MobHeadSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new MobHeadSpecialRenderer.Unbaked());
        @Override public MapCodec<MobHeadSpecialRenderer.Unbaked> type() { return MAP_CODEC; }
        @Override public @Nullable SpecialModelRenderer<?> bake(BakingContext ctx) {
            return new MobHeadSpecialRenderer();
        }
    }
}
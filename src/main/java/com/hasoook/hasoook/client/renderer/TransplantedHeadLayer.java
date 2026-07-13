package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.duck.HeadRemovedAccess;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.*;

/**
 * 头部移植渲染层。
 * <p>
 * 核心定位逻辑：
 * <ol>
 *   <li>用目标模型头部 pivot 定位到目标头部 pivot（颈关节）位置</li>
 *   <li>外来头部克隆时 <b>清零根部 pose</b>，防止 submitModelPart 叠加偏移；子部件保留原始 pose</li>
 *   <li><b>X/Y = 0</b>：pivot 本身就是颈关节中心位置，不做额外偏移</li>
 *   <li><b>Z 轴后方对齐</b>：将外来头部包围盒后方（maxZ）对齐到目标头部后方，
 *       让长脸生物的面部自然前伸，避免脸陷进身体或头身分离</li>
 * </ol>
 */
public class TransplantedHeadLayer extends RenderLayer<LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {

    private static final Map<String, ModelPart> HEAD_CLONES = new HashMap<>();
    /** 外来头部包围盒缓存 {minX, minY, minZ, maxX, maxY, maxZ}（像素，相对根 pivot） */
    private static final Map<String, float[]> HEAD_BOUNDS_CACHE = new HashMap<>();
    /** 目标头部包围盒缓存（按模型类，{minX, minY, minZ, maxX, maxY, maxZ}） */
    private static final Map<Class<?>, float[]> TARGET_BOUNDS_CACHE = new HashMap<>();
    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();
    /** 目标生物从 root 到 head 路径缓存 */
    private static final Map<EntityModel<?>, List<ModelPart>> TARGET_PATH_CACHE = new HashMap<>();
    /** 已打印调试信息的类型集合（一次性日志） */
    private static final Set<String> DEBUGGED_TYPES = new HashSet<>();

    private static final Identifier FALLBACK_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    @SuppressWarnings({"rawtypes", "unchecked"})
    public TransplantedHeadLayer(LivingEntityRenderer renderer) {
        super((RenderLayerParent<LivingEntityRenderState, EntityModel<LivingEntityRenderState>>) renderer);
    }

    @Override
    public void submit(
            PoseStack ps,
            SubmitNodeCollector collector,
            int packedLight,
            LivingEntityRenderState state,
            float limbSwing,
            float limbSwingAmount
    ) {
        if (!(state instanceof HeadRemovedAccess access)) return;
        String transplantType = access.hasoook$getTransplantedHeadType();
        if (transplantType == null || transplantType.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Identifier typeId = Identifier.tryParse(transplantType);
        if (typeId == null) return;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(typeId);
        if (entityType == null) return;

        // === 获取/缓存外来头部 ===
        // 玩家头需要按 UUID 区分缓存（不同玩家皮肤不同）；普通生物按类型缓存
        boolean isPlayerHead = "minecraft:player".equals(transplantType);
        String cacheKey = transplantType;
        if (isPlayerHead) {
            String puuid = access.hasoook$getTransplantedPlayerUuid();
            if (puuid != null && !puuid.isEmpty()) {
                cacheKey = transplantType + ":" + puuid;
            }
        }

        ModelPart headClone = HEAD_CLONES.get(cacheKey);
        float[] foreignBounds = HEAD_BOUNDS_CACHE.get(cacheKey);

        if (headClone == null) {
            // 玩家实体无法直接在客户端创建，用僵尸作为代理（两者共享相同的头部模型）
            Entity entity;
            if (isPlayerHead) {
                entity = EntityType.ZOMBIE.create(mc.level, EntitySpawnReason.LOAD);
            } else {
                entity = entityType.create(mc.level, EntitySpawnReason.LOAD);
            }
            if (entity == null) return;
            try {
                freezeEntityPose(entity);
                EntityRenderer<? super Entity, ?> renderer =
                        mc.getEntityRenderDispatcher().getRenderer(entity);
                ModelPart headPart = findHeadFromRenderer(renderer);
                if (headPart == null) return;

                // ★ 清零根部 pose，子部件保留原始 pose
                headClone = deepClonePartWithZeroPose(headPart);
                HEAD_CLONES.put(cacheKey, headClone);

                // 计算外来头包围盒
                foreignBounds = computeTotalBounds(headClone);
                HEAD_BOUNDS_CACHE.put(cacheKey, foreignBounds);

                // 普通生物 → 缓存纹理；玩家头不缓存（每帧重取）
                if (!isPlayerHead) {
                    Identifier tex = getEntityTexture(renderer, entity);
                    if (tex == null) tex = FALLBACK_TEXTURE;
                    TEXTURE_CACHE.put(cacheKey, tex);
                }
            } finally {
                entity.discard();
            }
        }

        // ★ 玩家头纹理：每帧重新获取（不缓存），避免 PlayerInfo.getSkin()
        //   首帧返回默认皮肤后被永久缓存
        Identifier texture;
        if (isPlayerHead) {
            texture = getPlayerSkinTexture(
                    access.hasoook$getTransplantedPlayerUuid(),
                    access.hasoook$getTransplantedPlayerName());
        } else {
            texture = TEXTURE_CACHE.get(cacheKey);
        }

        if (headClone == null || foreignBounds == null) return;
        if (texture == null) {
            texture = FALLBACK_TEXTURE;
        }

        // === 目标头部 pivot 定位 ===
        EntityModel<?> targetModel = getParentModel();
        List<ModelPart> path = TARGET_PATH_CACHE.computeIfAbsent(
                targetModel, m -> findPathToHead(m.root()));
        if (path == null || path.isEmpty()) return;

        // 获取目标头部包围盒
        ModelPart targetHeadPart = path.get(path.size() - 1);
        float[] targetBounds = TARGET_BOUNDS_CACHE.computeIfAbsent(
                targetModel.getClass(), k -> computeTotalBounds(targetHeadPart));

        // 一次性调试日志：目标头部 pivot 和 path
        if (DEBUGGED_TYPES.add("target:" + targetModel.getClass().getSimpleName())) {
            float[] neck = neckPoint(targetBounds);
            StringBuilder pathStr = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                ModelPart p = path.get(i);
                var pose = p.getInitialPose();
                pathStr.append(String.format(Locale.ROOT, "[%d pos=(%.1f,%.1f,%.1f)]",
                        i, pose.x(), pose.y(), pose.z()));
            }
            Hasoook.LOGGER.info(
                    "[TransplantedHead] TARGET  {} bounds=X[{},{}] Y[{},{}] Z[{},{}] "
                    + "size=({},{},{}) neck=({},{},{}) path_len={} path={}",
                    targetModel.getClass().getSimpleName(),
                    fmt(targetBounds[0]), fmt(targetBounds[3]),
                    fmt(targetBounds[1]), fmt(targetBounds[4]),
                    fmt(targetBounds[2]), fmt(targetBounds[5]),
                    fmt(targetBounds[3] - targetBounds[0]),
                    fmt(targetBounds[4] - targetBounds[1]),
                    fmt(targetBounds[5] - targetBounds[2]),
                    fmt(neck[0]), fmt(neck[1]), fmt(neck[2]),
                    path.size(), pathStr.toString());
        }

        // === 渲染 ===
        ps.pushPose();
        // 沿路径累积变换到目标头部 pivot
        for (ModelPart part : path) {
            part.translateAndRotate(ps);
        }

        // ★ 颈关节点对齐
        // X: 不做平移 — 包围盒反射计算结果受 clone 清零 pose 影响不可靠，
        //     而 pivot 本身就是颈关节左右中心，保持 0 即可
        // Y: 有鼻子的头（村民/女巫/掠夺者/行商等）移植后会高一像素。
        //    由于这些模型的头部根立方体因兜帽/胡子等延展到脖子以下，
        //    导致 getCoreMinY 返回的"脖子位置"不准确，
        //    这里统一做 -1 像素补偿。
        // Z: 对齐头部"后方"（maxZ），让长脸生物（牛/羊/山羊）的面部自然前伸，
        //     避免脸陷进身体或头身分离
        float foreignNeckZ = foreignBounds[5];  // maxZ = 头部后方
        float targetNeckZ = targetBounds[5];    // maxZ = 头部后方

        // 有鼻子的头（村民/女巫/掠夺者/行商等）移植后 Y 轴会高一像素。
        //    正 Y 偏移使头部向下移动（此坐标系中正Y=头向身体方向）。
        //    经验证：+3 偏低，+1 恰好对齐脖子。
        final ModelPart foreignHeadForCheck = headClone;
        float yOffset = hasNoseChild(foreignHeadForCheck) ? 1.5F / 16.0F : 0.0F;

        if (DEBUGGED_TYPES.add("align:" + transplantType + "→" + targetModel.getClass().getSimpleName())) {
            Hasoook.LOGGER.info(
                    "[TransplantedHead] ALIGN {}→{} foreignZ={} targetZ={} noseComp={} translate=(Y={},Z={})",
                    transplantType, targetModel.getClass().getSimpleName(),
                    fmt(foreignNeckZ), fmt(targetNeckZ),
                    yOffset != 0 ? "YES(-1px)" : "NO",
                    fmt(yOffset),
                    fmt((targetNeckZ - foreignNeckZ) / 16.0F));
        }

        ps.translate(
                0,
                yOffset,
                (targetNeckZ - foreignNeckZ) / 16.0F
        );

        RenderType renderType = RenderTypes.entityCutoutNoCull(texture);
        collector.submitModelPart(headClone, ps, renderType, packedLight, OverlayTexture.NO_OVERLAY,
                null, false, false, -1, null, 0);

        ps.popPose();
    }

    // ==================== 小工具 ====================

    /** 格式化浮点数到一位小数，用于日志输出 */
    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** 检查 ModelPart 是否包含 "nose" 子部件（村民/女巫/掠夺者等生物的头） */
    private static boolean hasNoseChild(ModelPart part) {
        for (String name : part.children.keySet()) {
            if (name.equalsIgnoreCase("nose")) {
                return true;
            }
        }
        return false;
    }

    // ==================== 颈关节点计算 ====================

    /**
     * 从包围盒计算颈关节点（头盖骨底部后方与脖子相连处）。
     *
     * @return {neckX, neckY, neckZ}（像素，相对于 pivot）
     */
    private static float[] neckPoint(float[] bounds) {
        return new float[]{
                (bounds[0] + bounds[3]) / 2f,  // centerX
                bounds[1],                       // minY（底部 = 脖子连接处）
                bounds[5]                        // maxZ（后方 = 脖子连接处）
        };
    }

    // ==================== 头部立方体计算 ====================

    /**
     * 计算 ModelPart 子树中所有立方体的总包围盒（并集），正确累加子部件 initialPose。
     * 返回 {minX, minY, minZ, maxX, maxY, maxZ}（像素），所有坐标相对于根部件 pivot。
     */
    private static float[] computeTotalBounds(ModelPart headPart) {
        float[] total = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        // 根部件偏移从 (0,0,0) 开始，不叠加根部件自身的 initialPose（与清零后的克隆体一致）
        collectCubesWithOffset(headPart, 0, 0, 0, total);
        if (total[0] == Float.MAX_VALUE) {
            // 默认 8×8×8 标准头，pivot 在中心（玩家/僵尸/村民等）
            return new float[]{-4, -4, -4, 4, 4, 4};
        }
        return total;
    }

    /**
     * 递归收集所有立方体到总包围盒，累加子部件 initialPose 偏移，
     * 使所有立方体坐标统一到根部件 pivot 空间。
     */
    @SuppressWarnings("unchecked")
    private static void collectCubesWithOffset(ModelPart part, float offX, float offY, float offZ, float[] total) {
        for (ModelPart.Cube cube : (List<ModelPart.Cube>) part.cubes) {
            float[] bounds = computeCubeBounds(cube);
            if (bounds == null) continue;
            if (bounds[0] + offX < total[0]) total[0] = bounds[0] + offX;
            if (bounds[1] + offY < total[1]) total[1] = bounds[1] + offY;
            if (bounds[2] + offZ < total[2]) total[2] = bounds[2] + offZ;
            if (bounds[3] + offX > total[3]) total[3] = bounds[3] + offX;
            if (bounds[4] + offY > total[4]) total[4] = bounds[4] + offY;
            if (bounds[5] + offZ > total[5]) total[5] = bounds[5] + offZ;
        }
        for (ModelPart child : ((Map<String, ModelPart>) part.children).values()) {
            collectCubesWithOffset(child,
                    offX + child.initialPose.x(),
                    offY + child.initialPose.y(),
                    offZ + child.initialPose.z(),
                    total);
        }
    }

    /**
     * 计算头部模型的核心 minY（脖子连接处）。
     * 仅查看根部件自身的立方体，排除鼻子、帽子、挂饰等子部件的干扰。
     * 这与 {@code MobHeadBlockRenderer.computeHeadInfo} 中 {@code alignMaxY} 的思路一致。
     *
     * @param headPart 头部模型部件（或其克隆体）
     * @return 核心 minY（像素，相对于该部件的 pivot），若无立方体则返回默认值 -4
     */
    @SuppressWarnings("unchecked")
    private static float getCoreMinY(ModelPart headPart) {
        float coreMinY = Float.MAX_VALUE;
        int rootCubeCount = 0;
        int successCount = 0;
        List<ModelPart.Cube> rootCubes = (List<ModelPart.Cube>) headPart.cubes;
        for (ModelPart.Cube cube : rootCubes) {
            rootCubeCount++;
            float[] bounds = computeCubeBounds(cube);
            if (bounds != null) {
                successCount++;
                if (bounds[1] < coreMinY) {
                    coreMinY = bounds[1];
                }
            }
        }
        if (coreMinY == Float.MAX_VALUE) {
            // 根部件无立方体或所有立方体计算失败（罕见情况），回退到标准 8 像素高头部
            Hasoook.LOGGER.warn("[TransplantedHead] getCoreMinY fallback to -4: rootCubes={} success={}",
                    rootCubeCount, successCount);
            return -4.0f;
        }
        return coreMinY;
    }

    /** 一次性日志：打印 Polygon/Quad 类的字段名 */
    private static volatile boolean polyFieldsLogged = false;

    @org.jspecify.annotations.Nullable
    private static float[] computeCubeBounds(ModelPart.Cube cube) {
        float[] bounds = tryOriginDimensionFields(cube);
        if (bounds != null) return bounds;
        return tryPolygonVertices(cube);
    }

    /** 一次性日志：打印 Cube 类的字段名，用于诊断反射兼容性 */
    private static volatile boolean cubeFieldsLoggedTransplant = false;

    @org.jspecify.annotations.Nullable
    private static float[] tryOriginDimensionFields(ModelPart.Cube cube) {
        try {
            Class<?> clazz = cube.getClass();
            if (!cubeFieldsLoggedTransplant) {
                cubeFieldsLoggedTransplant = true;
                StringBuilder sb = new StringBuilder("[TransplantedHead] Cube fields: ");
                for (Field f : clazz.getDeclaredFields()) {
                    sb.append(f.getName()).append("(").append(f.getType().getSimpleName()).append(") ");
                }
                Hasoook.LOGGER.info(sb.toString());
            }
            Float ox = null, oy = null, oz = null;
            Float dx = null, dy = null, dz = null;
            Float gx = null, gy = null, gz = null;
            boolean hasMaxField = false;
            for (Field f : clazz.getDeclaredFields()) {
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
            if (ox == null || oy == null || oz == null || dx == null || dy == null || dz == null) return null;
            if (hasMaxField) { dx = dx - ox; dy = dy - oy; dz = dz - oz; }
            float gxVal = gx != null ? gx : 0f;
            float gyVal = gy != null ? gy : 0f;
            float gzVal = gz != null ? gz : 0f;
            return new float[]{ox - gxVal, oy - gyVal, oz - gzVal, ox + dx + gxVal, oy + dy + gyVal, oz + dz + gzVal};
        } catch (Exception e) { return null; }
    }

    @org.jspecify.annotations.Nullable
    private static float[] tryPolygonVertices(ModelPart.Cube cube) {
        try {
            Class<?> cubeClass = cube.getClass();
            Object[] polygons = readArrayField(cube, cubeClass, "polygons", "quads");
            if (polygons == null) return null;
            float[] bounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Object poly : polygons) {
                if (poly == null) continue;
                Object[] vertices = readArrayField(poly, poly.getClass(), "vertices");
                if (vertices == null) continue;
                for (Object vert : vertices) {
                    if (vert == null) continue;
                    Object pos = readField(vert, vert.getClass(), "pos", "position");
                    if (pos instanceof org.joml.Vector3fc v) {
                        if (v.x() < bounds[0]) bounds[0] = v.x();
                        if (v.y() < bounds[1]) bounds[1] = v.y();
                        if (v.z() < bounds[2]) bounds[2] = v.z();
                        if (v.x() > bounds[3]) bounds[3] = v.x();
                        if (v.y() > bounds[4]) bounds[4] = v.y();
                        if (v.z() > bounds[5]) bounds[5] = v.z();
                    }
                }
            }
            if (bounds[0] == Float.MAX_VALUE) return null;
            return bounds;
        } catch (Exception ignored) { return null; }
    }

    @org.jspecify.annotations.Nullable
    private static Object readField(Object obj, Class<?> clazz, String... names) {
        for (String name : names) {
            try { Field f = clazz.getDeclaredField(name); f.setAccessible(true); return f.get(obj); }
            catch (Exception ignored) {}
        }
        return null;
    }

    @org.jspecify.annotations.Nullable
    private static Object[] readArrayField(Object obj, Class<?> clazz, String... names) {
        Object val = readField(obj, clazz, names);
        if (val instanceof Object[] arr) return arr;
        if (val instanceof List<?> list) return list.toArray();
        return null;
    }

    private static boolean matchesDim(String n, String a) { return n.contains(a) && !n.contains("xr") && !n.contains("yr") && !n.contains("zr"); }
    private static boolean isOrigin(String n) { return n.contains("origin"); }
    private static boolean isMin(String n) { return n.contains("min") || n.contains("from"); }
    private static boolean isMax(String n) { return n.contains("max") || n.contains("to"); }
    private static boolean isDimension(String n) { return n.contains("dimension"); }
    private static boolean isSize(String n) { return n.contains("size"); }
    private static boolean isGrow(String n) { return n.contains("grow") || n.contains("inflate") || n.contains("expand"); }

    // ==================== 模型搜索 ====================

    private static void freezeEntityPose(Entity entity) {
        entity.setYRot(0); entity.setXRot(0);
        entity.yRotO = 0; entity.xRotO = 0;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0; living.yHeadRotO = 0;
            living.yBodyRot = 0; living.yBodyRotO = 0;
        }
    }

    @org.jspecify.annotations.Nullable
    private static ModelPart findHeadFromRenderer(EntityRenderer<?, ?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> lr)) return null;
        return findHead(lr.getModel().root());
    }

    /**
     * 寻找从 root 到 head 的 ModelPart 层级路径。
     * 返回路径上所有部件（不含 root，含 head），按层级顺序排列。
     * <p>
     * 优先级：head → 凋灵头 → head_parts 容器 → 深层递归 → body 兜底
     */
    @org.jspecify.annotations.Nullable
    private static List<ModelPart> findPathToHead(ModelPart part) {
        List<ModelPart> path = findPathToHeadStrict(part);
        if (path != null) return path;
        return null;
    }

    /** 严格递归搜索 head/body 路径 */
    @org.jspecify.annotations.Nullable
    private static List<ModelPart> findPathToHeadStrict(ModelPart part) {
        // 1. 直接子节点: "head"
        if (part.hasChild("head")) {
            return List.of(part.getChild("head"));
        }
        // 2. 凋灵的头
        for (String name : new String[]{"center_head", "right_head", "left_head"}) {
            if (part.hasChild(name)) {
                return List.of(part.getChild(name));
            }
        }
        // 3. 马/驴/骡: "head_parts" 容器 → 定位到内部的 "head"
        if (part.hasChild("head_parts")) {
            ModelPart headParts = part.getChild("head_parts");
            if (headParts.hasChild("head")) {
                return List.of(headParts, headParts.getChild("head"));
            }
            return List.of(headParts);
        }
        // 4. 递归搜索非 head 子部件（如 bone→body→head 等深层嵌套）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (name.contains("head")) continue;
            List<ModelPart> subPath = findPathToHeadStrict(entry.getValue());
            if (subPath != null) {
                List<ModelPart> path = new ArrayList<>();
                path.add(entry.getValue());
                path.addAll(subPath);
                return path;
            }
        }
        // 5. 搜索名称含 "head" 的深层部件
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.equals("head_parts") && name.contains("head")) {
                List<ModelPart> inner = findPathToHeadStrict(entry.getValue());
                if (inner != null) {
                    List<ModelPart> path = new ArrayList<>();
                    path.add(entry.getValue());
                    path.addAll(inner);
                    return path;
                }
                return List.of(entry.getValue());
            }
            List<ModelPart> subPath = findPathToHeadStrict(entry.getValue());
            if (subPath != null) {
                List<ModelPart> path = new ArrayList<>();
                path.add(entry.getValue());
                path.addAll(subPath);
                return path;
            }
        }
        // 6. 搜索 "body"（无头生物兜底）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            if (entry.getKey().toLowerCase().equals("body")) {
                return List.of(entry.getValue());
            }
        }
        // 7. 搜索容器内的 "body"（如 root→bone→body）
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (name.contains("head")) continue;
            if (entry.getValue().hasChild("body")) {
                return List.of(entry.getValue(), entry.getValue().getChild("body"));
            }
        }
        return null;
    }

    /**
     * 在模型中寻找代表"头部"的 ModelPart（用于克隆外来头部模型）。
     * <p>
     * 优先级：head → 凋灵头 → head_parts 容器 → 深层递归 → body → body@容器内 → 整个模型
     */
    @org.jspecify.annotations.Nullable
    private static ModelPart findHead(ModelPart part) {
        ModelPart found = findHeadStrict(part);
        if (found != null) return found;
        // ★ 最终兜底：连 body 也没有 → 返回整个模型根部件（蠹虫等小生物）
        return part;
    }

    /** 严格递归搜索 head/body，不使用 root 兜底 */
    @org.jspecify.annotations.Nullable
    private static ModelPart findHeadStrict(ModelPart part) {
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
        // 6. 搜索 "body"（无头生物的兜底，如鱿鱼、恶魂）
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

    // ==================== 纹理 ====================

    /**
     * 根据玩家 UUID 和名称获取皮肤纹理（同步，优先从玩家列表获取已解析的皮肤）。
     * <p>
     * 关键：PlayerInfo 持有完整的 GameProfile（含纹理属性），内部使用
     * {@code SkinManager.createLookup()}，该方法在皮肤解析完成后持久返回真实纹理。
     * 对于本地玩家，皮肤在第一帧渲染时已解析完毕，因此调用立即返回真实皮肤。
     */
    private static Identifier getPlayerSkinTexture(@org.jspecify.annotations.Nullable String uuidString,
                                                    @org.jspecify.annotations.Nullable String playerName) {
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
        } catch (Exception ignored) {
            // UUID 格式错误或皮肤加载失败 → fallback
        }
        return FALLBACK_TEXTURE;
    }

    @org.jspecify.annotations.Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Identifier getEntityTexture(EntityRenderer<?, ?> renderer, Entity entity) {
        if (renderer instanceof LivingEntityRenderer lr) {
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            EntityRenderState renderState = dispatcher.extractEntity(entity, 0.0F);
            if (renderState instanceof LivingEntityRenderState livingState) {
                return lr.getTextureLocation(livingState);
            }
        }
        return null;
    }

    // ==================== 深拷贝（清零 pose） ====================

    /**
     * 深拷贝 ModelPart，根部清零，子部件保留原始 pose。
     */
    @SuppressWarnings("unchecked")
    private static ModelPart deepClonePartWithZeroPose(ModelPart source) {
        List<ModelPart.Cube> sourceCubes = (List<ModelPart.Cube>) source.cubes;
        Map<String, ModelPart> sourceChildren = (Map<String, ModelPart>) source.children;
        Map<String, ModelPart> clonedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, ModelPart> entry : sourceChildren.entrySet()) {
            clonedChildren.put(entry.getKey(), deepClonePartKeepPose(entry.getValue()));
        }
        return new ModelPart(sourceCubes, clonedChildren);
    }

    /** 深拷贝并保留 pose（用于子部件，如 nose、hat 等） */
    @SuppressWarnings("unchecked")
    private static ModelPart deepClonePartKeepPose(ModelPart source) {
        List<ModelPart.Cube> sourceCubes = (List<ModelPart.Cube>) source.cubes;
        Map<String, ModelPart> sourceChildren = (Map<String, ModelPart>) source.children;
        Map<String, ModelPart> clonedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, ModelPart> entry : sourceChildren.entrySet()) {
            clonedChildren.put(entry.getKey(), deepClonePartKeepPose(entry.getValue()));
        }
        ModelPart clone = new ModelPart(sourceCubes, clonedChildren);
        clone.setInitialPose(source.initialPose);
        clone.loadPose(source.initialPose);
        return clone;
    }
}

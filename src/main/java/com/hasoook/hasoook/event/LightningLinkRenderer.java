package com.hasoook.hasoook.event;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.HeavyHalberdProjectile;
import com.hasoook.hasoook.item.custom.ChargedCopperPickaxeItem;
import com.hasoook.hasoook.item.custom.ChargedCopperSwordItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.*;

/**
 * 闪电链渲染器 — 支持重戟、蓄电铜剑、蓄电铜镐三种闪电链。
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class LightningLinkRenderer {

    private static final double RANGE = 8.0D;
    private static final double SEARCH_RADIUS = 64.0D;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lightning());
        Matrix4f matrix = poseStack.last().pose();

        long timeSeed = mc.level.getGameTime();

        // ── 重戟闪电链（并联，保持原有逻辑）──
        renderHalberdChains(mc, matrix, consumer, timeSeed, camera);

        // ── 蓄电铜剑闪电链（串联）──
        renderSwordChains(mc, matrix, consumer, timeSeed, camera);

        // ── 蓄电铜镐闪电链（方块）──
        renderPickaxeChains(mc, matrix, consumer, timeSeed, camera);

        bufferSource.endBatch(RenderTypes.lightning());
        poseStack.popPose();
    }

    // ════════════════════════════════════════════════════════
    // 重戟闪电链（保持原有逻辑 — 并联）
    // ════════════════════════════════════════════════════════

    private static void renderHalberdChains(Minecraft mc, Matrix4f matrix,
                                            VertexConsumer consumer, long timeSeed, Camera camera) {
        AABB area = mc.player.getBoundingBox().inflate(SEARCH_RADIUS);
        List<HeavyHalberdProjectile> projectiles =
                mc.level.getEntitiesOfClass(HeavyHalberdProjectile.class, area);

        for (HeavyHalberdProjectile projectile : projectiles) {
            if (projectile.getChainTicks() <= 0) continue;

            Vec3 start = projectile.position();
            List<LivingEntity> targets = mc.level.getEntitiesOfClass(LivingEntity.class,
                    projectile.getBoundingBox().inflate(RANGE),
                    e -> e.isAlive() && e != projectile.getOwner());

            for (LivingEntity target : targets) {
                Vec3 end = target.position().add(0, target.getBbHeight() * 0.5, 0);
                drawLightning(matrix, consumer, start, end,
                        timeSeed + projectile.getId() * 31L, camera, 1.0F);
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // 蓄电铜剑闪电链（串联：玩家→目标→最近→次近→…）
    // ════════════════════════════════════════════════════════

    private static void renderSwordChains(Minecraft mc, Matrix4f matrix,
                                          VertexConsumer consumer, long timeSeed, Camera camera) {
        List<Player> players = mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(SEARCH_RADIUS));

        for (Player player : players) {
            // 检查主手和副手
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            ItemStack held;
            boolean isMainHand;
            if (ChargedCopperSwordItem.isChargedCopperSword(mainHand)) {
                held = mainHand;
                isMainHand = true;
            } else if (ChargedCopperSwordItem.isChargedCopperSword(offHand)) {
                held = offHand;
                isMainHand = false;
            } else continue;

            int chainTicks = ChargedCopperSwordItem.getChainTicks(held);
            if (chainTicks <= 0) continue;

            Vec3 targetPos = ChargedCopperSwordItem.getChainPos(held);
            if (targetPos == null) continue;

            int charge = ChargedCopperSwordItem.getCharge(held);
            float widthMultiplier = ChargedCopperSwordItem.getLightningWidthMultiplier(charge);

            // ── 计算手部偏移和弧度 ──
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            // 右向量 = look × up（Minecraft 中 Y 轴朝上）
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            double handSign = isMainHand ? 1.0 : -1.0; // 主手向右偏，副手向左偏

            // 起点：从眼睛向手持方向轻微偏移
            Vec3 start = eye.add(right.scale(0.3 * handSign)).add(0, -0.3, 0);

            // 弧度中点：向右手方向弯出一个弧
            double dist = start.distanceTo(targetPos);
            Vec3 toTarget = targetPos.subtract(start);
            Vec3 mid = start.add(toTarget.scale(0.3))
                    .add(right.scale(0.8 * handSign));

            // ── 构建串联路径：起点 → 弧度中点 → 目标 → 最近 → 次近 → … ──
            List<Vec3> chainPoints = new ArrayList<>();
            chainPoints.add(start);
            chainPoints.add(mid);
            chainPoints.add(targetPos);

            // 搜索并串联周围实体
            AABB searchBox = new AABB(targetPos, targetPos).inflate(RANGE);
            List<LivingEntity> allEntities = mc.level.getEntitiesOfClass(
                    LivingEntity.class, searchBox,
                    e -> e != player);
            Set<LivingEntity> visited = new HashSet<>();
            Vec3 current = targetPos;
            while (true) {
                LivingEntity nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (LivingEntity e : allEntities) {
                    if (visited.contains(e)) continue;
                    Vec3 ep = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    double d = current.distanceTo(ep);
                    if (d < RANGE && d < nearestDist) {
                        nearestDist = d;
                        nearest = e;
                    }
                }
                if (nearest == null) break;
                visited.add(nearest);
                Vec3 ep = nearest.position().add(0, nearest.getBbHeight() * 0.5, 0);
                chainPoints.add(ep);
                current = ep;
            }

            // 绘制串联闪电
            for (int i = 0; i < chainPoints.size() - 1; i++) {
                Vec3 from = chainPoints.get(i);
                Vec3 to = chainPoints.get(i + 1);
                drawLightning(matrix, consumer, from, to,
                        timeSeed + player.getId() * 31L + i * 7L,
                        camera, widthMultiplier);
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // 蓄电铜镐闪电链（从被挖方块到被连锁方块）
    // ════════════════════════════════════════════════════════

    private static void renderPickaxeChains(Minecraft mc, Matrix4f matrix,
                                            VertexConsumer consumer, long timeSeed, Camera camera) {
        List<Player> players = mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(SEARCH_RADIUS));

        for (Player player : players) {
            ItemStack held = player.getMainHandItem();
            if (!ChargedCopperPickaxeItem.isChargedCopperPickaxe(held)) continue;

            int chainTicks = ChargedCopperPickaxeItem.getChainTicks(held);
            if (chainTicks <= 0) continue;

            Vec3 origin = ChargedCopperPickaxeItem.getChainPos(held);
            if (origin == null) continue;

            int charge = ChargedCopperPickaxeItem.getCharge(held);
            float widthMultiplier = ChargedCopperPickaxeItem.getLightningWidthMultiplier(charge);

            // 读取被连锁的方块位置
            List<BlockPos> chainBlocks = ChargedCopperPickaxeItem.getChainBlocks(held);

            long seedBase = timeSeed + player.getId() * 31L;
            int idx = 0;
            for (BlockPos bp : chainBlocks) {
                Vec3 target = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                drawLightning(matrix, consumer, origin, target,
                        seedBase + idx * 7L, camera, widthMultiplier);
                idx++;
            }
        }
    }


    /**
     * 构建串联闪电链路径：玩家头顶上方 → 目标中心 → 最近实体 → 次近 → …
     * 包含已死亡实体（尸体/残骸），让视觉效果更震撼。
     * 起点从玩家眼睛上方偏移，避免第一人称遮挡准星。
     */
    private static List<Vec3> buildSerialChain(Minecraft mc, Player player, Vec3 targetPos) {
        List<Vec3> points = new ArrayList<>();

        // 起点：玩家眼睛下方 0.3 格，避开准星
        points.add(player.getEyePosition().add(0, -0.3, 0));

        // 第二点：服务器记录的目标位置
        points.add(targetPos);

        // 搜索所有 LivingEntity（包含已死亡的），排除玩家
        AABB searchBox = new AABB(targetPos, targetPos).inflate(RANGE);
        List<LivingEntity> allEntities = mc.level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player // 只排除玩家自己，允许已死亡实体
        );

        // 贪心最近邻串联
        Set<LivingEntity> visited = new HashSet<>();
        Vec3 current = targetPos;

        while (true) {
            LivingEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (LivingEntity e : allEntities) {
                if (visited.contains(e)) continue;
                Vec3 ep = e.position().add(0, e.getBbHeight() * 0.5, 0);
                double d = current.distanceTo(ep);
                if (d < RANGE && d < nearestDist) {
                    nearestDist = d;
                    nearest = e;
                }
            }

            if (nearest == null) break;

            visited.add(nearest);
            Vec3 ep = nearest.position().add(0, nearest.getBbHeight() * 0.5, 0);
            points.add(ep);
            current = ep;
        }

        return points;
    }

    // ════════════════════════════════════════════════════════
    // 共用闪电绘制
    // ════════════════════════════════════════════════════════

    /**
     * 绘制单条闪电弧线。
     *
     * @param widthMultiplier 宽度倍率（蓄电值越高越大，重戟固定为 1.0）
     */
    private static void drawLightning(Matrix4f matrix, VertexConsumer consumer,
                                      Vec3 start, Vec3 end,
                                      long seed, Camera camera,
                                      float widthMultiplier) {

        RandomSource random = RandomSource.create(seed);
        double distance = start.distanceTo(end);

        // 蓄电越高分段越多，闪电更曲折细长
        int segments = Math.max(10, (int) (distance * (6 + widthMultiplier * 3)));

        // 振幅随蓄电增大
        double ampScale = 0.25 * (0.6 + widthMultiplier * 0.4);

        Vec3 dir = end.subtract(start).normalize();
        Vec3 current = start;
        double step = distance / segments;

        for (int i = 1; i <= segments; i++) {
            Vec3 next;
            if (i == segments) {
                next = end;
            } else {
                next = start.add(dir.scale(i * step));

                double t = (double) i / segments;
                double amp = ampScale * Math.sin(t * Math.PI);
                double highFreq = Math.sin((t * 20 + seed % 100) * 3.14);

                Vec3 offset = new Vec3(
                        random.nextFloat() - 0.5,
                        random.nextFloat() - 0.5,
                        random.nextFloat() - 0.5
                ).normalize().scale(amp * (0.5 + highFreq * 0.5));

                next = next.add(offset);
            }

            // 靠近起点更粗、靠近终点更细（锥形闪电）
            double progress = (double) i / segments;
            float taper = (float) (1.0 - progress * 0.4);

            float width = 0.02f + random.nextFloat() * 0.02f;

            // ── 超宽辉光（高蓄电时启用）──
            if (widthMultiplier > 1.3f) {
                drawBillboardLine(matrix, consumer, current, next,
                        width * 6f * widthMultiplier * taper, camera,
                        50, 100, 240, 20);
            }

            // ── 外层辉光 ──
            drawBillboardLine(matrix, consumer, current, next,
                    width * 2f * widthMultiplier * taper, camera,
                    80, 140, 255, 50);

            // ── 内层亮芯 — 高蓄电偏白 ──
            int r = 255;
            int g = Math.min(255, 200 + (int) ((widthMultiplier - 1) * 55));
            int b = Math.min(255, 220 + (int) ((widthMultiplier - 1) * 35));
            drawBillboardLine(matrix, consumer, current, next,
                    width * 0.5f * widthMultiplier * taper, camera,
                    r, g, b, 255);

            // ── 分支闪电（概率和长度随蓄电增加）──
            float branchChance = 0.3f + (widthMultiplier - 1) * 0.15f;
            if (i < segments - 1 && random.nextFloat() < branchChance) {
                Vec3 branchDir = dir.add(new Vec3(
                        random.nextFloat() - 0.5,
                        random.nextFloat() - 0.5,
                        random.nextFloat() - 0.5
                ).scale(1.5)).normalize();

                double branchLen = step * (1.0 + widthMultiplier * 0.5);
                Vec3 branchEnd = current.add(branchDir.scale(branchLen));

                drawBillboardLine(matrix, consumer,
                        current, branchEnd,
                        width * widthMultiplier * taper,
                        camera,
                        100, 160, 255, 55);
            }

            current = next;
        }
    }

    private static void drawBillboardLine(Matrix4f matrix, VertexConsumer consumer,
                                          Vec3 start, Vec3 end, float width,
                                          Camera camera,
                                          int r, int g, int b, int a) {

        Vec3 camDir = new Vec3(
                camera.forwardVector().x(),
                camera.forwardVector().y(),
                camera.forwardVector().z()
        );

        Vec3 lineDir = end.subtract(start).normalize();
        Vec3 perpendicular = lineDir.cross(camDir);

        if (perpendicular.lengthSqr() < 1e-4) {
            perpendicular = lineDir.cross(new Vec3(0, 1, 0));
        }

        perpendicular = perpendicular.normalize().scale(width);

        Vec3 p1 = start.add(perpendicular);
        Vec3 p2 = start.subtract(perpendicular);
        Vec3 p3 = end.subtract(perpendicular);
        Vec3 p4 = end.add(perpendicular);

        addVertex(consumer, matrix, p1, r, g, b, a);
        addVertex(consumer, matrix, p2, r, g, b, a);
        addVertex(consumer, matrix, p3, r, g, b, a);
        addVertex(consumer, matrix, p4, r, g, b, a);
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix,
                                  Vec3 pos, int r, int g, int b, int a) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(r, g, b, a);
    }
}

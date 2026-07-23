package com.hasoook.hasoook.event;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.entity.custom.HeavyHalberdProjectile;
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
import net.minecraft.world.item.Items;
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

        // ── 蓄电铜镐/斧/锄/铲闪电链（方块）──
        renderToolBlockChains(mc, matrix, consumer, timeSeed, camera);

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
            // 检查主手和副手（原版铜剑 + 储电附魔）
            HandInfo info = findChargeEnchantedSword(player.getMainHandItem(), true)
                    .or(() -> findChargeEnchantedSword(player.getOffhandItem(), false))
                    .orElse(null);
            if (info == null) continue;

            ItemStack held = info.held;
            int chainTicks = held.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), 0);
            if (chainTicks <= 0) continue;
            Long encoded = held.get(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get());
            if (encoded == null) continue;
            Vec3 targetPos = BlockPos.of(encoded).getCenter();
            int charge = held.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), 0);
            float widthMultiplier = getLightningWidthMultiplier(charge);

            // ── 计算手部偏移和弧度 ──
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            double handSign = info.isMainHand ? 1.0 : -1.0;

            Vec3 start = eye.add(right.scale(0.3 * handSign)).add(0, -0.3, 0);

            double dist = start.distanceTo(targetPos);
            Vec3 toTarget = targetPos.subtract(start);
            Vec3 mid = start.add(toTarget.scale(0.3))
                    .add(right.scale(0.8 * handSign));

            // ── 构建串联路径 ──
            List<Vec3> chainPoints = new ArrayList<>();
            chainPoints.add(start);
            chainPoints.add(mid);
            chainPoints.add(targetPos);

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

            for (int i = 0; i < chainPoints.size() - 1; i++) {
                Vec3 from = chainPoints.get(i);
                Vec3 to = chainPoints.get(i + 1);
                drawLightning(matrix, consumer, from, to,
                        timeSeed + player.getId() * 31L + i * 7L,
                        camera, widthMultiplier);
            }
        }
    }

    /** 手部信息（用于区分主手/副手） */
    private record HandInfo(ItemStack held, boolean isMainHand) {}

    /** 检查物品是否为有储电附魔的铜剑，返回手部信息 */
    private static Optional<HandInfo> findChargeEnchantedSword(ItemStack stack, boolean isMainHand) {
        if (!stack.is(Items.COPPER_SWORD)) return Optional.empty();
        if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CHARGE, stack) <= 0) return Optional.empty();
        return Optional.of(new HandInfo(stack, isMainHand));
    }

    // ════════════════════════════════════════════════════════
    // 蓄电工具闪电链（方块：镐/斧/锄/铲）
    // ════════════════════════════════════════════════════════

    private static void renderToolBlockChains(Minecraft mc, Matrix4f matrix,
                                              VertexConsumer consumer, long timeSeed, Camera camera) {
        List<Player> players = mc.level.getEntitiesOfClass(Player.class,
                mc.player.getBoundingBox().inflate(SEARCH_RADIUS));

        for (Player player : players) {
            ItemStack held = player.getMainHandItem();

            // 只处理原版铜工具且有储电附魔
            if (!isVanillaCopperTool(held)) continue;
            if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CHARGE, held) <= 0) continue;

            int chainTicks = held.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), 0);
            if (chainTicks <= 0) continue;

            Long encoded = held.get(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get());
            if (encoded == null) continue;
            Vec3 origin = BlockPos.of(encoded).getCenter();

            int charge = held.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), 0);
            float widthMultiplier = getLightningWidthMultiplier(charge);

            List<BlockPos> chainBlocks = readToolChainBlocks(held);
            if (chainBlocks.isEmpty()) continue;

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


    // ════════════════════════════════════════════════════════
    // 工具函数
    // ════════════════════════════════════════════════════════

    /** 判断是否为原版铜工具 */
    private static boolean isVanillaCopperTool(ItemStack stack) {
        return stack.is(Items.COPPER_PICKAXE)
                || stack.is(Items.COPPER_AXE)
                || stack.is(Items.COPPER_SHOVEL)
                || stack.is(Items.COPPER_HOE);
    }

    /** 从数据组件读取连锁方块列表 */
    private static List<BlockPos> readToolChainBlocks(ItemStack stack) {
        String raw = null;
        if (stack.is(Items.COPPER_PICKAXE))
            raw = stack.get(ModDataComponents.CHARGED_COPPER_PICKAXE_CHAIN_BLOCKS.get());
        else if (stack.is(Items.COPPER_AXE))
            raw = stack.get(ModDataComponents.CHARGED_COPPER_AXE_CHAIN_BLOCKS.get());
        else if (stack.is(Items.COPPER_SHOVEL))
            raw = stack.get(ModDataComponents.CHARGED_COPPER_SHOVEL_CHAIN_BLOCKS.get());
        else if (stack.is(Items.COPPER_HOE))
            raw = stack.get(ModDataComponents.CHARGED_COPPER_HOE_CHAIN_BLOCKS.get());

        if (raw == null || raw.isEmpty()) return List.of();
        List<BlockPos> list = new ArrayList<>();
        for (String part : raw.split(";")) {
            String[] xyz = part.split(",");
            if (xyz.length == 3) try {
                list.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            } catch (NumberFormatException ignored) {}
        }
        return list;
    }

    /** 闪电宽度倍率（基于蓄电值） */
    public static float getLightningWidthMultiplier(int charge) {
        return Math.min(3.0F, 1.0F + charge / 40.0F * 0.8F);
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

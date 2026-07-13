package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 蓄电铜镐 — 通过避雷针+铜箱子充能。
 * 破坏方块时释放闪电链随机破坏周围同类型方块。
 */
public class ChargedCopperPickaxeItem extends Item {

    private static final int CHAIN_DURATION_TICKS = 10;
    private static final int BASE_RADIUS = 2;

    public ChargedCopperPickaxeItem(Properties properties) {
        super(properties);
    }

    // ──── 蓄电值（与剑共用数据组件） ──────────────────────────

    public static int getCharge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), Math.max(0, charge));
    }

    public static boolean isChargedCopperPickaxe(ItemStack stack) {
        return stack.getItem() instanceof ChargedCopperPickaxeItem;
    }

    // ──── 闪电链数据 ────────────────────────────────────────

    public static int getChainTicks(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), 0);
    }

    public static void setChainTicks(ItemStack stack, int ticks) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), Math.max(0, ticks));
    }

    public static Vec3 getChainPos(ItemStack stack) {
        Long encoded = stack.get(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get());
        if (encoded == null) return null;
        BlockPos bp = BlockPos.of(encoded);
        return new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
    }

    public static void setChainPos(ItemStack stack, Vec3 pos) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get(),
                BlockPos.containing(pos).asLong());
    }

    public static List<BlockPos> getChainBlocks(ItemStack stack) {
        String raw = stack.get(ModDataComponents.CHARGED_COPPER_PICKAXE_CHAIN_BLOCKS.get());
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

    public static void setChainBlocks(ItemStack stack, Collection<BlockPos> blocks) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos bp : blocks) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(bp.getX()).append(',').append(bp.getY()).append(',').append(bp.getZ());
        }
        stack.set(ModDataComponents.CHARGED_COPPER_PICKAXE_CHAIN_BLOCKS.get(), sb.toString());
    }

    public static float getLightningWidthMultiplier(int charge) {
        return ChargedCopperSwordItem.getLightningWidthMultiplier(charge);
    }

    // ──── 方块破坏 ──────────────────────────────────────────

    @Override
    public boolean mineBlock(@NonNull ItemStack stack, @NonNull Level level,
                             @NonNull BlockState state, @NonNull BlockPos pos,
                             @NonNull LivingEntity miner) {
        if (level.isClientSide()) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE))
            return super.mineBlock(stack, level, state, pos, miner);

        int charge = getCharge(stack);
        if (charge <= 0) return super.mineBlock(stack, level, state, pos, miner);

        int radius = BASE_RADIUS + charge / 20;
        int maxBlocks = 2 + charge / 10;

        List<BlockPos> sameBlocks = new ArrayList<>();
        for (BlockPos bp : BlockPos.betweenClosed(
                pos.offset(-radius, -radius, -radius),
                pos.offset(radius, radius, radius))) {
            if (bp.equals(pos)) continue;
            if (level.getBlockState(bp).is(state.getBlock()))
                sameBlocks.add(bp.immutable());
        }
        if (sameBlocks.isEmpty()) return super.mineBlock(stack, level, state, pos, miner);

        sameBlocks.sort(Comparator.comparingDouble(pos::distSqr));
        List<BlockPos> toBreak = new ArrayList<>();
        for (BlockPos bp : sameBlocks) {
            if (toBreak.size() >= maxBlocks) break;
            double dist = Math.sqrt(pos.distSqr(bp));
            double chance = Math.max(0.1, 1.0 - dist / (radius * 2.0));
            if (level.random.nextDouble() < chance) toBreak.add(bp);
        }
        if (toBreak.isEmpty()) toBreak.add(sameBlocks.get(0));

        ServerPlayer player = miner instanceof ServerPlayer sp ? sp : null;
        for (BlockPos bp : toBreak) {
            BlockState bs = level.getBlockState(bp);
            if (player != null) {
                Block.dropResources(bs, level, bp, level.getBlockEntity(bp), player, stack);
            } else {
                Block.dropResources(bs, level, bp, level.getBlockEntity(bp), miner, stack);
            }
            level.removeBlock(bp, false);
        }

        for (BlockPos bp : toBreak) {
            Vec3 center = Vec3.atCenterOf(bp);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    center.x, center.y, center.z, 8 + charge / 10, 0.3, 0.3, 0.3, 0.05);
        }
        Vec3 origin = Vec3.atCenterOf(pos);
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                origin.x, origin.y, origin.z, 12, 0.3, 0.3, 0.3, 0.05);
        serverLevel.playSound(null, pos, SoundEvents.COPPER_BREAK,
                miner.getSoundSource(), 0.9F, 1.4F);

        setChainPos(stack, origin);
        setChainBlocks(stack, toBreak);
        setChainTicks(stack, CHAIN_DURATION_TICKS);
        setCharge(stack, charge - 1);

        return super.mineBlock(stack, level, state, pos, miner);
    }

    // ──── tick ──────────────────────────────────────────────

    @Override
    public void inventoryTick(@NonNull ItemStack stack, ServerLevel level,
                              @NonNull Entity entity, @Nullable EquipmentSlot slot) {
        int remaining = getChainTicks(stack);
        if (remaining > 0) setChainTicks(stack, remaining - 1);

        // 蓄电值变动时更新挖掘等级
        int charge = getCharge(stack);
        int bracket = charge / 10;
        Integer lastBracket = stack.get(ModDataComponents.CHARGED_COPPER_PICKAXE_TOOL_CHARGE.get());
        if (lastBracket == null || lastBracket != bracket) {
            applyToolForBracket(stack, bracket);
            stack.set(ModDataComponents.CHARGED_COPPER_PICKAXE_TOOL_CHARGE.get(), bracket);
        }
    }

    private static void applyToolForBracket(ItemStack stack, int bracket) {
        float speed = Math.min(8.0F, 5.0F + bracket * 0.25F);
        var incorrectTag = bracket >= 3 ? BlockTags.INCORRECT_FOR_DIAMOND_TOOL
                : bracket >= 1 ? BlockTags.INCORRECT_FOR_IRON_TOOL
                : BlockTags.INCORRECT_FOR_COPPER_TOOL;

        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(BlockTags.MINEABLE_WITH_PICKAXE).orElseThrow();
        var incorrect = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(incorrectTag).orElseThrow();

        stack.set(net.minecraft.core.component.DataComponents.TOOL,
                new net.minecraft.world.item.component.Tool(List.of(
                        net.minecraft.world.item.component.Tool.Rule.deniesDrops(incorrect),
                        net.minecraft.world.item.component.Tool.Rule.minesAndDrops(blocks, speed)
                ), 1.0F, 1, true));
    }

    // ──── 提示框 ────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay tooltipDisplay, Consumer<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);
        int charge = getCharge(stack);
        if (charge > 0) {
            tooltipComponents.accept(Component.translatable(
                    "tooltip.hasoook.charged_copper_sword.charge", charge)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.accept(Component.translatable(
                    "tooltip.hasoook.charged_copper_sword.no_charge")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

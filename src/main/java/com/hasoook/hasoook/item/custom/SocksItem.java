package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class SocksItem extends Item {

    /// 磨损阶段阈值（指数增长，类似玩家经验值 — 越往后越难升级）
    /// 每级所需时间：10s → 15s → 25s → 40s → 60s → 100s
    private static final int STAGE_0_MAX = 199;     // 崭新出厂     (0~10秒)
    private static final int STAGE_1_MAX = 499;     // 略有异味     (10~25秒)
    private static final int STAGE_2_MAX = 999;     // 明显汗臭     (25~50秒)
    private static final int STAGE_3_MAX = 1799;    // 熏人鼻腔     (50~90秒)
    private static final int STAGE_4_MAX = 2999;    // 令人窒息     (90~150秒)
    private static final int STAGE_5_MAX = 4999;    // 生化武器     (150~250秒)
    // 超过 STAGE_5_MAX → 不可名状 (250秒+ ≈ 4分钟)

    public SocksItem(Properties properties) {
        super(properties);
    }

    /**
     * 根据磨损值返回当前阶段 (0-6)
     */
    public static int getStage(int wear) {
        if (wear <= STAGE_0_MAX) return 0;
        if (wear <= STAGE_1_MAX) return 1;
        if (wear <= STAGE_2_MAX) return 2;
        if (wear <= STAGE_3_MAX) return 3;
        if (wear <= STAGE_4_MAX) return 4;
        if (wear <= STAGE_5_MAX) return 5;
        return 6;
    }

    /**你
     * 阶段文本 + 颜色 — 越来越黑暗
     */
    public static Component getStageTooltip(int stage) {
        return switch (stage) {
            case 0 -> Component.translatable("tooltip.hasoook.socks.fresh")
                    .withStyle(ChatFormatting.GRAY);
            case 1 -> Component.translatable("tooltip.hasoook.socks.slight_odor")
                    .withStyle(ChatFormatting.GRAY);
            case 2 -> Component.translatable("tooltip.hasoook.socks.sweat_stench")
                    .withStyle(ChatFormatting.GREEN);
            case 3 -> Component.translatable("tooltip.hasoook.socks.nose_burning")
                    .withStyle(ChatFormatting.GREEN);
            case 4 -> Component.translatable("tooltip.hasoook.socks.suffocating")
                    .withStyle(ChatFormatting.DARK_GREEN);
            case 5 -> Component.translatable("tooltip.hasoook.socks.bioweapon")
                    .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD);
            case 6 -> Component.translatable("tooltip.hasoook.socks.indescribable")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            default -> Component.empty();
        };
    }

    public static int getMinWearForStage(int stage) {
        if (stage <= 0) return 0;
        if (stage >= STAGE_MIN_WEAR.length) stage = STAGE_MIN_WEAR.length - 1;
        return STAGE_MIN_WEAR[stage];
    }

    /// 各阶段最小磨损值（用于清洗后重置到目标阶段起点）
    private static final int[] STAGE_MIN_WEAR = {
            0,      // stage 0
            200,    // stage 1
            500,    // stage 2
            1000,   // stage 3
            1800,   // stage 4
            3000,   // stage 5
            5000,   // stage 6
    };

    /**
     * 右键炼药锅清洗袜子：降低2个臭味等级，消耗1层水。
     * 返回 SUCCESS 阻止游戏触发装备行为。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        // 只有水的炼药锅才能清洗
        if (!state.is(Blocks.WATER_CAULDRON)) {
            return super.useOn(context);
        }

        ItemStack stack = context.getItemInHand();
        int currentWear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
        int currentStage = getStage(currentWear);

        if (currentStage == 0) {
            return InteractionResult.PASS; // 干净的袜子，不消耗事件
        }

        if (!level.isClientSide()) {
            // 降低 2 级，最低为 0
            int targetStage = Math.max(0, currentStage - 2);
            stack.set(ModDataComponents.SOCKS_WEAR.get(), STAGE_MIN_WEAR[targetStage]);

            // ── 减少炼药锅水位 ──
            int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
            ServerLevel serverLevel = (ServerLevel) level;

            if (waterLevel > 1) {
                level.setBlock(pos, state.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
            } else {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
            }

            // ── 音效 ──
            serverLevel.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6f, 1.2f);

            // ── 粒子 ──
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    8, 0.25, 0.1, 0.25, 0.05);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay tooltipDisplay, Consumer<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);

        int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
        tooltipComponents.accept(getStageTooltip(getStage(wear)));
    }
}

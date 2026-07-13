package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class EchoBottleItem extends Item {

    public EchoBottleItem(Properties properties) {
        super(properties.rarity(Rarity.RARE));
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, Player player, @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity entity, @NonNull ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;

        int used = this.getUseDuration(stack, entity) - remainingUseDuration;

        // 使用1秒
        if (used >= 20) {
            player.stopUsingItem(); // 自动停止
        }
    }

    @Override
    public void onStopUsing(@NonNull ItemStack stack, @NonNull LivingEntity entity, int count) {
        if ((entity instanceof Player player)) {
            player.getCooldowns().addCooldown(stack, 20);
        }
        super.onStopUsing(stack, entity, count);
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay tooltipDisplay, @NonNull Consumer<Component> tooltipAdder, @NonNull TooltipFlag flag) {
        String soundIdStr = stack.get(ModDataComponents.RECORDED_SOUND.get());

        if (soundIdStr != null && !soundIdStr.isEmpty()) {
            Identifier soundId = Identifier.tryParse(soundIdStr);

            if (soundId != null) {
                Component translatedSound = null;

                if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
                    translatedSound = com.hasoook.hasoook.util.ClientSoundHelper.getAccurateSubtitle(soundId);
                }

                if (translatedSound == null) {
                    // 尝试猜测常规的翻译键
                    String translationKey = soundId.getNamespace().equals("minecraft")
                            ? "subtitles." + soundId.getPath()
                            : "subtitles." + soundId.getNamespace() + "." + soundId.getPath();

                    if (net.minecraft.locale.Language.getInstance().has(translationKey)) {
                        translatedSound = Component.translatable(translationKey);
                    } else {
                        // 连猜测的翻译都没有，只能把英文美化一下显示出来
                        String fallbackText = soundId.getPath().replace('.', ' ').replace('_', ' ');
                        if (!fallbackText.isEmpty()) {
                            fallbackText = fallbackText.substring(0, 1).toUpperCase() + fallbackText.substring(1);
                        }
                        translatedSound = Component.literal(fallbackText);
                    }
                }

                tooltipAdder.accept(translatedSound.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }

        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
    }

    @Override
    public boolean onEntitySwing(@NonNull ItemStack stack, @NonNull LivingEntity entity, @NonNull InteractionHand hand) {
        if (!(entity instanceof Player player)) {
            return super.onEntitySwing(stack, entity, hand);
        }

        // 检查是否在冷却中
        if (player.getCooldowns().isOnCooldown(stack)) {
            return super.onEntitySwing(stack, entity, hand);
        }

        // 尝试获取瓶子里记录的声音
        String soundIdStr = stack.get(ModDataComponents.RECORDED_SOUND.get());

        if (soundIdStr != null && !soundIdStr.isEmpty()) {
            Level level = player.level();

            // 播放声音
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                Identifier soundId = Identifier.tryParse(soundIdStr);

                if (soundId != null) {
                    // 播放声音事件
                    SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
                    level.playSound(null, player.blockPosition(), soundEvent, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

                    // 粒子效果
                    serverLevel.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY() + player.getEyeHeight() * 0.5, player.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.05D);

                    // 发送游戏事件（振动）
                    level.gameEvent(player, GameEvent.NOTE_BLOCK_PLAY, player.position());
                }
            }

            player.getCooldowns().addCooldown(stack, 20);
        }
        return super.onEntitySwing(stack, entity, hand);
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.getBlockState(pos).is(Blocks.CALIBRATED_SCULK_SENSOR)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            String soundIdStr = stack.get(ModDataComponents.RECORDED_SOUND.get());

            if (blockEntity != null && soundIdStr != null && !soundIdStr.isEmpty()) {
                if (!level.isClientSide()) {
                    blockEntity.setData(ModAttachments.FILTERED_SOUND_ID.get(), soundIdStr);
                    blockEntity.setChanged();

                    stack.shrink(1); // 消耗一个物品

                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.hasoook.calibrated_sculk_sensor"), true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return super.useOn(context);
    }

    // 最大使用时间
    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return 72000;
    }

    // 使用动画
    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
}
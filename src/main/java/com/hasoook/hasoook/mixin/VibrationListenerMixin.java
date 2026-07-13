package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.custom.EchoBottleItem;
import com.hasoook.hasoook.util.SoundVibrationTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMixin implements GameEventListener {

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void hasoook$filterEchoBottleVibration(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        // 【关键】如果是重发阶段，直接放行给原版处理
        if (SoundVibrationTracker.BYPASS_MIXIN.get()) {
            return;
        }

        Optional<Vec3> listenerPosOpt = this.getListenerSource().getPosition(level);
        if (listenerPosOpt.isEmpty()) return;

        BlockPos blockPos = BlockPos.containing(listenerPosOpt.get());
        BlockEntity be = level.getBlockEntity(blockPos);

        if (be instanceof CalibratedSculkSensorBlockEntity) {
            String requiredSoundId = be.getData(ModAttachments.FILTERED_SOUND_ID.get());

            // 如果该感测体被校准过声音
            if (requiredSoundId != null && !requiredSoundId.isEmpty()) {
                Entity source = context.sourceEntity();

                // 1. 特例：玩家挥动了装有对应声音的回音瓶
                if (gameEvent.value() == GameEvent.NOTE_BLOCK_PLAY.value() && source instanceof Player player) {
                    ItemStack mainHand = player.getMainHandItem();
                    ItemStack offHand = player.getOffhandItem();
                    ItemStack bottle = ItemStack.EMPTY;

                    if (mainHand.getItem() instanceof EchoBottleItem) bottle = mainHand;
                    else if (offHand.getItem() instanceof EchoBottleItem) bottle = offHand;

                    if (!bottle.isEmpty()) {
                        String bottleSoundId = bottle.getOrDefault(ModDataComponents.RECORDED_SOUND.get(), "");
                        if (requiredSoundId.equals(bottleSoundId)) {
                            // 是对应的瓶子！直接原样放行，立即产生振动
                            return;
                        }
                    }
                }

                // 2. 如果不是挥动瓶子，此时原版还没来得及发声，我们无法立刻判断
                // 所以我们强制拦截这次原版事件，把它丢给队列，等 Tick 末尾再核对
                cir.setReturnValue(false);

                SoundVibrationTracker.queuePendingEvent(
                        level,
                        (VibrationSystem.Listener)(Object)this, // 把当前的监听器传进去
                        gameEvent,
                        context,
                        pos,
                        requiredSoundId
                );
            }
        }
    }
}
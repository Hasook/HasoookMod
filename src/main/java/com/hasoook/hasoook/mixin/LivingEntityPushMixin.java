package com.hasoook.hasoook.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当玩家的视角绑定到其他实体时（getCamera != this），
 * 取消 {@link LivingEntity#pushEntities()} 调用，
 * 防止玩家本体碰撞箱推动目标实体。
 */
@Mixin(LivingEntity.class)
public class LivingEntityPushMixin {

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void hasoook$skipPushIfCameraBound(CallbackInfo ci) {
        // noinspection ConstantValue
        if ((Object) this instanceof ServerPlayer player) {
            if (player.getCamera() != player) {
                ci.cancel();
            }
        }
    }
}

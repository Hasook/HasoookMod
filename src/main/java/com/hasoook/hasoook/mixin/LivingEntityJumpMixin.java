package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 一级伤残：禁止跳跃。
 * <p>
 * {@code LivingEntity#jumpFromGround()} 是实体跳跃的唯一入口，
 * 在这里注入 HEAD 并取消即可阻止一切跳跃行为。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void cancelJumpIfDisabled(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.hasEffect(ModEffects.DISABILITY)) {
            ci.cancel();
        }
    }
}

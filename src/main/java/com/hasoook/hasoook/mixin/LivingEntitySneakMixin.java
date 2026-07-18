package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 一级伤残：禁止潜行。
 * <p>
 * {@code Entity#setShiftKeyDown(boolean)} 在 Entity 上定义且被 LivingEntity 继承，
 * 此 Mixin 注入 Entity 以避免方法解析问题。
 */
@Mixin(Entity.class)
public abstract class LivingEntitySneakMixin {

    @Inject(method = "setShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void cancelSneakIfDisabled(boolean sneaking, CallbackInfo ci) {
        if (!sneaking) return; // 允许松手
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && living.hasEffect(ModEffects.DISABILITY)) {
            ci.cancel();
        }
    }
}

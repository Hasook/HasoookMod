package com.hasoook.hasoook.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void onHitEntity(EntityHitResult result, CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        Entity hit = result.getEntity();

        // 如果击中的实体是骑着自己的实体，则取消爆炸
        if (rocket.getPassengers().contains(hit)) {
            ci.cancel();
        }
    }
}

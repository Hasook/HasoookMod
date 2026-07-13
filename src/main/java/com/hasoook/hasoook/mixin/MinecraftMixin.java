package com.hasoook.hasoook.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 当玩家视角绑定到其他实体时（附身状态），禁用所有玩家交互行为：
 * <ul>
 *   <li>左键攻击/破坏方块</li>
 *   <li>右键使用物品/交互方块</li>
 *   <li>持续破坏方块</li>
 * </ul>
 * 防止玩家透过生物视角攻击自己身体导致服务端踢出。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hasoook$cancelAttackWhenCameraBound(CallbackInfoReturnable<Boolean> cir) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player != null && self.getCameraEntity() != self.player) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void hasoook$cancelUseItemWhenCameraBound(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player != null && self.getCameraEntity() != self.player) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void hasoook$cancelContinueAttackWhenCameraBound(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.player != null && self.getCameraEntity() != self.player) {
            ci.cancel();
        }
    }
}

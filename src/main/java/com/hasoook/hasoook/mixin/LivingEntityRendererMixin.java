package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.component.SockFaceData;
import com.hasoook.hasoook.duck.SockFaceAccess;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void hasoook$syncSockFace(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        if (renderState instanceof SockFaceAccess sockAccess) {
            String sockFaceData = SockFaceData.getSockFace(entity);
            sockAccess.hasoook$setSockFaceData(sockFaceData != null ? sockFaceData : "");
        }
    }
}

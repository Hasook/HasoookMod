package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.duck.HeadRemovedAccess;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void hasoook$syncHeadRemoved(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        if (renderState instanceof HeadRemovedAccess access) {
            // 获取实体的无头状态并存入渲染状态
            boolean headRemoved = Boolean.TRUE.equals(entity.getData(ModAttachments.HEAD_REMOVED.get()));
            access.hasoook$setHeadRemoved(headRemoved);

            // 同步移植头部类型
            String transplantedType = entity.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
            access.hasoook$setTransplantedHeadType(transplantedType != null && !transplantedType.isEmpty() ? transplantedType : null);

            // 同步移植玩家头 UUID（用于皮肤查询）
            String playerUuid = entity.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get());
            access.hasoook$setTransplantedPlayerUuid(playerUuid != null && !playerUuid.isEmpty() ? playerUuid : null);

            // 同步移植玩家头名称（用于皮肤查询）
            String playerName = entity.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get());
            access.hasoook$setTransplantedPlayerName(playerName != null && !playerName.isEmpty() ? playerName : null);
        }
    }
}

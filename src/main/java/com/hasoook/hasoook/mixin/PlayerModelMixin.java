package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.Config;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.item.Items;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void customMapHoldingPose(AvatarRenderState state, CallbackInfo ci) {
        if (!Config.PAQ_DISPLAY_MAP.get()) {
            return;
        }

        PlayerModel model = (PlayerModel) (Object) this;

        boolean holdMapInOneHand = (state.rightHandItemStack.is(Items.FILLED_MAP) && state.leftHandItemStack.isEmpty()) || (state.leftHandItemStack.is(Items.FILLED_MAP) && state.rightHandItemStack.isEmpty());

        if (holdMapInOneHand) {
            model.rightArm.xRot = -0.55F;
            model.leftArm.xRot = -0.55F;
            model.rightArm.yRot = -0.35F;
            model.leftArm.yRot = 0.35F;
            model.rightArm.zRot = 0.0F;
            model.leftArm.zRot = 0.0F;
        }
    }
}
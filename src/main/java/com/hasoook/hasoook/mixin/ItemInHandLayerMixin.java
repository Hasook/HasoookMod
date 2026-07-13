package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 展示型地图：取消渲染手上的地图物品。
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void hasoook$hideMap(
            ArmedEntityRenderState state,
            ItemStackRenderState itemState,
            ItemStack stack,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci
    ) {
        // 如果开启了展示型地图，就取消渲染原本的地图物品
        if (!Config.PAQ_DISPLAY_MAP.get()) {
            return;
        }

        if (stack.is(Items.FILLED_MAP)) {
            ci.cancel();
        }
    }
}
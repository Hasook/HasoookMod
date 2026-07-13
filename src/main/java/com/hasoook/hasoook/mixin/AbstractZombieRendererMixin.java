package com.hasoook.hasoook.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractZombieRenderer.class)
public abstract class AbstractZombieRendererMixin {

    /**
     * 修复原版 bug：AbstractZombieRenderer.getArmPose() 检查了对侧手臂的物品来决定当前手臂的姿态。
     * 这导致僵尸主手拿矛时，副手也被设为 SPEAR 姿态。
     * 此处复现基类 HumanoidMobRenderer 的正确逻辑：检查当前手臂的物品。
     */
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private void fixGetArmPose(Zombie zombie, HumanoidArm arm, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        ItemStack stack = zombie.getItemHeldByArm(arm);
        SwingAnimation swing = stack.get(DataComponents.SWING_ANIMATION);
        if (swing != null && swing.type() == SwingAnimationType.STAB) {
            cir.setReturnValue(HumanoidModel.ArmPose.SPEAR);
        } else if (stack.is(ItemTags.SPEARS)) {
            cir.setReturnValue(HumanoidModel.ArmPose.SPEAR);
        } else {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
        }
    }
}

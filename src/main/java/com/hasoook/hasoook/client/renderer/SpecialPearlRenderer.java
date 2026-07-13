package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SpecialPearlRenderer implements SpecialModelRenderer<ItemStack> {
    public static final SpecialPearlRenderer INSTANCE = new SpecialPearlRenderer();

    public static void attach(ItemStackRenderState state, ItemStack stack) {
        state.newLayer().setupSpecialModel(INSTANCE, stack);
    }

    public void submit(ItemStack ignored, @NonNull ItemDisplayContext context, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND) {
            poseStack.pushPose();
            if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                poseStack.translate(0.7F, 1.5F, 0.38F);
                poseStack.mulPose(Axis.XN.rotationDegrees(10.0F));
            } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
                poseStack.translate(0.3F, 1.5F, 0.38F);
                poseStack.mulPose(Axis.XN.rotationDegrees(10.0F));
            } else {
                poseStack.translate(0.5F, 1.62F, 0.64F);
            }

            ItemStack itemStack = SlimeSpearItem.getStoredItem(ignored);
            if (itemStack.isEmpty()) {
                poseStack.popPose();
            } else {
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    poseStack.scale(0.5F, 0.5F, 0.5F);
                } else if (itemStack.get(DataComponents.TOOL) != null) {
                    poseStack.scale(1.0F, 1.0F, 0.8F);
                    poseStack.translate(0.0F, 0.2F, -0.02F);
                    poseStack.mulPose(Axis.XN.rotationDegrees(44.0F));
                } else if (itemStack.get(DataComponents.DAMAGE_TYPE) != null) {
                    poseStack.scale(1.0F, 1.2F, 1.2F);
                    poseStack.translate(0.0F, 0.3F, -0.02F);
                    poseStack.mulPose(Axis.XN.rotationDegrees(-45.0F));
                } else if (item instanceof ShieldItem) {
                    poseStack.mulPose(Axis.ZN.rotationDegrees(90.0F));
                    poseStack.translate(-0.3F, 0.5F, 0.5F);
                } else {
                    poseStack.scale(1.0F, 0.5F, 0.5F);
                }

                poseStack.mulPose(Axis.YN.rotationDegrees(90.0F));
                ItemStackRenderState state = new ItemStackRenderState();
                Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, itemStack, ItemDisplayContext.NONE, (Level)null, (ItemOwner)null, 0);
                state.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    public void getExtents(@NonNull Consumer<Vector3fc> consumer) {
    }

    public @Nullable ItemStack extractArgument(@NonNull ItemStack stack) {
        return stack;
    }
}

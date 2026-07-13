package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class SpecialPearlRenderer implements SpecialModelRenderer<ItemStack> {
    public static final SpecialPearlRenderer INSTANCE = new SpecialPearlRenderer();

    public static void attach(ItemRenderState state, ItemStack stack) {
        state.newLayer().setSpecialModel(INSTANCE, stack);
    }

    @Override
    public void render(ItemStack data, @NotNull ItemDisplayContext context, @NotNull MatrixStack matrices,
                       @NotNull OrderedRenderCommandQueue queue, int light, int overlay, boolean foil, int outline) {
        if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND) {
            matrices.push();
            if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                matrices.translate(0.7F, 1.5F, 0.38F);
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(10.0F));
            } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
                matrices.translate(0.3F, 1.5F, 0.38F);
                matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(10.0F));
            } else {
                matrices.translate(0.5F, 1.62F, 0.64F);
            }

            ItemStack itemStack = SlimeSpearItem.getStoredItem(data);
            if (itemStack.isEmpty()) {
                matrices.pop();
            } else {
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    matrices.scale(0.5F, 0.5F, 0.5F);
                } else if (itemStack.contains(DataComponentTypes.TOOL)) {
                    matrices.scale(1.0F, 1.0F, 0.8F);
                    matrices.translate(0.0F, 0.2F, -0.02F);
                    matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(44.0F));
                } else if (itemStack.contains(DataComponentTypes.DAMAGE_TYPE)) {
                    matrices.scale(1.0F, 1.2F, 1.2F);
                    matrices.translate(0.0F, 0.3F, -0.02F);
                    matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(-45.0F));
                } else if (item instanceof ShieldItem) {
                    matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(90.0F));
                    matrices.translate(-0.3F, 0.5F, 0.5F);
                } else {
                    matrices.scale(1.0F, 0.5F, 0.5F);
                }

                matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(90.0F));
                ItemRenderState state = new ItemRenderState();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    client.getItemModelManager()
                            .update(state, itemStack, ItemDisplayContext.NONE, client.world, null, 0);
                    state.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, 0);
                }
                matrices.pop();
            }
        }
    }

    @Override
    public void collectVertices(@NotNull Consumer<Vector3fc> consumer) {
    }

    @Override
    public @Nullable ItemStack getData(@NotNull ItemStack stack) {
        return stack;
    }
}

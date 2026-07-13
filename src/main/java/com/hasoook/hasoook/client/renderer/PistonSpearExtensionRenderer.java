package com.hasoook.hasoook.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class PistonSpearExtensionRenderer implements SpecialModelRenderer<PistonSpearExtensionRenderer.SpearRenderData> {

    public static final PistonSpearExtensionRenderer INSTANCE = new PistonSpearExtensionRenderer();

    public record SpearRenderData(
            ItemStackRenderState middleState,
            ItemStackRenderState headState,
            int rodCount
    ) {}

    public static void attach(ItemStackRenderState parentState, int rodCount, Item rodItem, Item headItem) {
        Minecraft minecraft = Minecraft.getInstance();

        ItemStackRenderState middleState = new ItemStackRenderState();
        ItemStackRenderState headState = new ItemStackRenderState();

        ItemStack middleStack = new ItemStack(rodItem);
        ItemStack headStack = new ItemStack(headItem);

        minecraft.getItemModelResolver().updateForTopItem(
                middleState, middleStack, ItemDisplayContext.NONE, null, null, 0);
        minecraft.getItemModelResolver().updateForTopItem(
                headState, headStack, ItemDisplayContext.NONE, null, null, 0);

        parentState.newLayer().setupSpecialModel(
                INSTANCE,
                new SpearRenderData(middleState, headState, rodCount)
        );
    }

    @Override
    public void submit(
            SpearRenderData data,
            @NonNull ItemDisplayContext context,
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean foil,
            int outline
    ) {
        if (context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F); // 居中

        // 根据渲染类型微调
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) { //主手
            poseStack.translate(0.2F, 0.735F, -0.1F);
            poseStack.mulPose(Axis.XN.rotationDegrees(10));
            poseStack.scale(0.8F,0.8F,0.8F);
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) { // 副手
            poseStack.translate(-0.2F, 0.735F, -0.1F);
            poseStack.mulPose(Axis.XN.rotationDegrees(10));
            poseStack.scale(0.8F,0.8F,0.8F);
        } else {
            poseStack.translate(0.0F, 0.8F, 0.1F);
        }

        // 旋转
        poseStack.mulPose(Axis.YN.rotationDegrees(90));
        poseStack.mulPose(Axis.ZN.rotationDegrees(45));

        int rodCount = data.rodCount(); // 获取连接杆的数量

        float rodHeight = 0.437F; // 连接杆长度

        // 循环渲染多节连接杆
        for (int i = 0; i < rodCount; i++) {
            poseStack.pushPose();

            poseStack.translate(i * -rodHeight, i * rodHeight, 0.0F);

            data.middleState().submit(
                    poseStack,
                    collector,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    outline
            );

            poseStack.popPose();
        }

        // 渲染矛头
        poseStack.pushPose();

        float headHeight = 1 - rodHeight;
        poseStack.translate(rodCount * -rodHeight + headHeight, rodCount * rodHeight - headHeight, 0.0F);

        data.headState().submit(
                poseStack,
                collector,
                light,
                OverlayTexture.NO_OVERLAY,
                outline
        );

        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public void getExtents(@NonNull Consumer<Vector3fc> consumer) {
    }

    @Override
    public @Nullable SpearRenderData extractArgument(@NonNull ItemStack stack) {
        return null; // 如果你想让这个值根据物品的 NBT/DataComponent 动态改变，可以在这里读取
    }
}
package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.entity.custom.SevowerProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.NonNull;

public class SevowerProjectileRenderer extends EntityRenderer<SevowerProjectile, SevowerProjectileRenderer.SevowerState> {

    // 用于解析物品模型的工具
    private final ItemModelResolver itemModelResolver;

    public SevowerProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public SevowerState createRenderState() {
        return new SevowerState();
    }

    @Override
    public void extractRenderState(SevowerProjectile entity, SevowerState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        // 1. 复制一份实体里的 ItemStack，避免修改原始数据
        ItemStack renderStack = entity.getItem().copy();

        // 2. 强制设置 CustomModelData 让它显示为进攻状态 (值为 1.0F)
        CustomModelData oldCmd = renderStack.getOrDefault(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData.EMPTY
        );

        java.util.List<Float> floats = new java.util.ArrayList<>(oldCmd.floats());
        if (floats.isEmpty()) floats.add(0F);
        floats.set(0, 1.0F); // 1.0F 对应进攻状态的贴图

        renderStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        floats,
                        oldCmd.flags(),
                        oldCmd.strings(),
                        oldCmd.colors()
                )
        );

        // 3. 核心：解析强制修改过状态的 renderStack
        this.itemModelResolver.updateForNonLiving(
                state.itemRenderState,
                renderStack,
                ItemDisplayContext.FIXED,
                entity
        );
    }

    @Override
    public void submit(SevowerState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();

        poseStack.translate(0.0, 0.15, 0.0);

        // 🪓 平放
        poseStack.mulPose(Axis.XP.rotationDegrees(90));

        // 🔄 旋转
        float rotation = state.ageInTicks * 40F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation));

        // ✅ 使用 submit 方法来提交渲染！
        if (!state.itemRenderState.isEmpty()) {
            state.itemRenderState.submit(
                    poseStack,
                    collector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor // 发光轮廓颜色
            );
        }

        poseStack.popPose();

        super.submit(state, poseStack, collector, cameraState);
    }

    // 自定义一个 State 类，用来携带物品渲染状态
    public static class SevowerState extends EntityRenderState {
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
    }

    @Override
    protected int getBlockLightLevel(SevowerProjectile entity, @NonNull BlockPos pos) {
        return 15;
    }
}
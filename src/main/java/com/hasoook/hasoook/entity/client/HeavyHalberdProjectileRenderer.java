package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.entity.custom.HeavyHalberdProjectile;
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
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.NonNull;

public class HeavyHalberdProjectileRenderer extends EntityRenderer<HeavyHalberdProjectile, HeavyHalberdProjectileRenderer.HeavyHalberdState> {

    private final ItemModelResolver itemModelResolver;

    public HeavyHalberdProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public HeavyHalberdState createRenderState() {
        return new HeavyHalberdState();
    }

    // 【新增】提取实体的飞行角度和物品模型
    @Override
    public void extractRenderState(HeavyHalberdProjectile entity, HeavyHalberdState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // 获取弹射物当前的偏航角(YRot)和俯仰角(XRot)
        state.yRot = entity.getYRot(partialTick);
        state.xRot = entity.getXRot(partialTick);

        // 解析物品模型数据供渲染使用
        this.itemModelResolver.updateForNonLiving(state.itemRenderState, entity.getItem(), ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(HeavyHalberdState state, PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState cameraState) {
        poseStack.pushPose();

        // 修正物品贴图自身的旋转角度
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot - 45.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(0F));

        if (!state.itemRenderState.isEmpty()) {
            state.itemRenderState.submit(
                    poseStack,
                    collector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );
        }

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }

    public static class HeavyHalberdState extends EntityRenderState {
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
        public float xRot;
        public float yRot;
    }
}
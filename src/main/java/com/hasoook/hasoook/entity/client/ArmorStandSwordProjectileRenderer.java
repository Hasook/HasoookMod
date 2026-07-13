package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.entity.custom.ArmorStandSwordProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.NonNull;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

public class ArmorStandSwordProjectileRenderer
        extends EntityRenderer<ArmorStandSwordProjectile, ArmorStandSwordProjectileRenderer.SwordState> {

    private final ItemModelResolver itemModelResolver;

    public ArmorStandSwordProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public SwordState createRenderState() {
        return new SwordState();
    }

    @Override
    public void extractRenderState(ArmorStandSwordProjectile entity, SwordState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        state.yRot = entity.getYRot(partialTick);
        state.xRot = entity.getXRot(partialTick);

        // 解析剑的完整物品模型（含通过 mixin attach() 附加的 3D 盔甲层）
        this.itemModelResolver.updateForNonLiving(
                state.itemRenderState,
                entity.getItem(),
                ItemDisplayContext.FIXED,
                entity
        );
    }

    @Override
    public void submit(SwordState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        poseStack.pushPose();

        // 三叉戟式朝向
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot - 135.0F));

        // 渲染剑模型 + attach() 附加的 3D 盔甲层
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

    public static class SwordState extends EntityRenderState {
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
        public float xRot;
        public float yRot;
    }
}

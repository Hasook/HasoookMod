package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.entity.custom.CardProjectile;
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
import net.minecraft.world.phys.Vec3;

public class CardProjectileRenderer extends EntityRenderer<CardProjectile, CardProjectileRenderer.CardState> {

    private final ItemModelResolver itemModelResolver;

    public CardProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public CardState createRenderState() {
        return new CardState();
    }

    @Override
    public void extractRenderState(CardProjectile entity, CardState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.stuck = entity.isStuck();
        state.velocity = entity.isStuck() ? entity.getStuckVelocity() : entity.getDeltaMovement();
        this.itemModelResolver.updateForNonLiving(
                state.itemRenderState, entity.getItem(), ItemDisplayContext.FIXED, entity);
    }

    @Override
    public void submit(CardState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.15, 0.0);
        poseStack.scale(0.5F, 0.5F, 0.5F);

        if (state.velocity.lengthSqr() > 0.0001) {
            Vec3 vel = state.velocity;
            double hDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float yaw = (float) Math.toDegrees(Math.atan2(vel.x, vel.z));
            float pitch = (float) Math.toDegrees(Math.atan2(vel.y, hDist));

            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(75.0F - pitch));
            if (!state.stuck)
                poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * 60F));
        } else {
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }

        if (!state.itemRenderState.isEmpty()) {
            state.itemRenderState.submit(poseStack, collector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }

    public static class CardState extends EntityRenderState {
        public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
        public boolean stuck;
        public Vec3 velocity = Vec3.ZERO;
    }
}

package com.hasoook.hasoook;

import com.hasoook.hasoook.client.SockOverlayHandler;
import com.hasoook.hasoook.client.renderer.SockFaceLayer;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.network.payload.SockFaceSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;

public class HasoookClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ── 实体渲染器（扔出的袜子投射物）──
        EntityRendererRegistry.register(ModEntities.THROWN_SOCK, FlyingItemEntityRenderer::new);

        // ── 袜子糊脸第三人称渲染层（仅玩家）──
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityType == EntityType.PLAYER && entityRenderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer) {
                        @SuppressWarnings("unchecked")
                        FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx =
                                (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) livingRenderer;
                        registrationHelper.register(new SockFaceLayer(ctx));
                    }
                }
        );

        // ── 接收服务端袜子糊脸同步 ──
        ClientPlayNetworking.registerGlobalReceiver(SockFaceSyncPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        com.hasoook.hasoook.component.SockFaceData.updateClientCache(
                                payload.targetUuid(), payload.data());
                    });
                }
        );

        // ── 第一人称/背包 HUD 袜子渲染 ──
        SockOverlayHandler.register();

        Hasoook.LOGGER.info("Hasoook client initialized!");
    }
}

package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Config;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

/**
 * 展示型地图：第三人称视角渲染玩家手中的地图。
 */
public class ThirdPersonMapLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier MAP_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "textures/map/map_background.png");
    private static final float TWO_HAND_SCALE = 0.0065F; // 双手持握时的地图尺寸
    private static final float ONE_HAND_SCALE = 0.004F; // 单手持握时的地图尺寸

    public ThirdPersonMapLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(
            @NonNull PoseStack poseStack,
            @NonNull SubmitNodeCollector nodeCollector,
            int packedLight,
            AvatarRenderState state,
            float limbSwing,
            float limbSwingAmount
    ) {
        if (!Config.PAQ_DISPLAY_MAP.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ItemStack rightItem = state.rightHandItemStack;
        ItemStack leftItem = state.leftHandItemStack;

        // 右手有地图：渲染在右手 (true)
        if (!rightItem.isEmpty() && rightItem.is(Items.FILLED_MAP)) {
            boolean twoHanded = leftItem.isEmpty();
            renderMapOnArm(poseStack, nodeCollector, packedLight, mc, rightItem, twoHanded, state, true);
        }
        // 左手有地图：渲染在左手 (false)
        if (!leftItem.isEmpty() && leftItem.is(Items.FILLED_MAP)) {
            boolean twoHanded = rightItem.isEmpty();
            renderMapOnArm(poseStack, nodeCollector, packedLight, mc, leftItem, twoHanded, state, false);
        }
    }

    private void renderMapOnArm(
            PoseStack poseStack,
            SubmitNodeCollector nodeCollector,
            int packedLight,
            Minecraft mc,
            ItemStack mapStack,
            boolean twoHanded,
            AvatarRenderState state,
            boolean isRightHand
    ) {
        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        if (mapId == null) return;

        MapItemSavedData mapData = null;
        if (mc.level != null) {
            mapData = mc.level.getMapData(mapId);
        }
        if (mapData == null) return;

        MapRenderer mapRenderer = mc.getMapRenderer();
        MapRenderState mapRenderState = new MapRenderState();
        mapRenderer.extractRenderState(mapId, mapData, mapRenderState);

        poseStack.pushPose();
        try {
            if (twoHanded) {
                // 双手持握
                this.getParentModel().body.translateAndRotate(poseStack);
                float offsetY = 0.45F;
                float offsetZ = -0.48F;
                float tiltX = -20F; // 旋转角度
                if (state.isCrouching) {
                    offsetY -= 0.4F;
                    offsetZ -= 0.1F;
                    tiltX = -30F;
                }
                poseStack.translate(0.0F, offsetY, offsetZ);
                poseStack.mulPose(Axis.XP.rotationDegrees(tiltX));
                poseStack.scale(TWO_HAND_SCALE, TWO_HAND_SCALE, TWO_HAND_SCALE);

                // 如果地图在主手且未潜行，将地图绕中心旋转180度，使正面朝向玩家
                if (!state.isCrouching && isRightHand) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                    poseStack.translate(-64.0F, -10.0F, 90.0F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
                } else {
                    poseStack.translate(-64.0F, -64.0F, 0.0F);
                }
            } else {
                // 单手持握（绑定右手或左手）
                if (isRightHand) {
                    this.getParentModel().rightArm.translateAndRotate(poseStack);
                } else {
                    this.getParentModel().leftArm.translateAndRotate(poseStack);
                }
                poseStack.translate(0.0F, 0.53F, -0.35F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(180F));
                poseStack.scale(ONE_HAND_SCALE, ONE_HAND_SCALE, ONE_HAND_SCALE);
                poseStack.translate(-64.0F, -64.0F, 0.0F);
            }

            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            RenderType backgroundType = RenderTypes.entityCutoutNoCull(MAP_BACKGROUND);
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(backgroundType);

            vertexConsumer.addVertex(matrix, 0, 128, 0).setColor(-1).setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
            vertexConsumer.addVertex(matrix, 128, 128, 0).setColor(-1).setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
            vertexConsumer.addVertex(matrix, 128, 0, 0).setColor(-1).setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
            vertexConsumer.addVertex(matrix, 0, 0, 0).setColor(-1).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);

            bufferSource.endBatch(backgroundType);

            poseStack.pushPose();
            try {
                float mapContentScale = 0.90F;
                poseStack.translate(64.0F, 64.0F, 0.0F);
                poseStack.scale(mapContentScale, mapContentScale, 1.0F);
                poseStack.translate(-64.0F, -64.0F, 0.0F);
                mapRenderer.render(mapRenderState, poseStack, nodeCollector, false, packedLight);
            } finally {
                poseStack.popPose();
            }
        } finally {
            poseStack.popPose();
        }
    }
}
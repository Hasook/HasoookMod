package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.duck.SockFaceAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Random;

public class SockFaceLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier[] TEXTURES = {
            Hasoook.id("textures/misc/sock_overlay.png"),
            Hasoook.id("textures/misc/sock_overlay_1.png"),
            Hasoook.id("textures/misc/sock_overlay_2.png"),
    };

    private static final float FACE_Z = -5.0f / 16.0f;
    private static final float FACE_Y = -5.0f / 16.0f;
    private static final float LAYER_Z_OFFSET = 0.001f;
    private static final float TEX_SIZE = 256.0f;
    private static final float QUAD_SIZE = 0.625f;

    public SockFaceLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    private static int remaining(int p) { return p & 0xFFF; }
    private static int seed(int p) { return (p >> 12) & 0xF; }
    private static int wearStage(int p) { return (p >> 16) & 0xFF; }
    private static int texIndex(int p) { return (p >> 24) & 0xFF; }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        if (!(state instanceof SockFaceAccess access)) return;

        String data = access.hasoook$getSockFaceData();
        if (data == null || data.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        VertexConsumerProvider.Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
        String[] parts = data.split(",");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            int packed;
            try {
                packed = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                continue;
            }

            int rem = remaining(packed);
            if (rem <= 0) continue;

            int ti = texIndex(packed);
            if (ti < 0 || ti >= TEXTURES.length) ti = 0;

            Random rand = new Random(seed(packed) * 7919L + ti * 6271L
                    + wearStage(packed) * 13337L + i * 31337L);

            matrices.push();

            this.getContextModel().head.applyTransform(matrices);

            float jitterX = (rand.nextFloat() - 0.5f) * 3.0f / 16.0f;
            float jitterY = (rand.nextFloat() - 0.5f) * 2.0f / 16.0f;
            float rotZ = (rand.nextFloat() - 0.5f) * 25.0f;

            float layerZ = FACE_Z + i * LAYER_Z_OFFSET;
            matrices.translate(jitterX, FACE_Y + jitterY, layerZ);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));

            float s = QUAD_SIZE / TEX_SIZE;
            matrices.scale(s, s, 1.0f);

            matrices.translate(-TEX_SIZE / 2.0f, -TEX_SIZE / 2.0f, 0.0f);

            var renderLayer = RenderLayers.entityCutoutNoCull(TEXTURES[ti]);
            float alpha = Math.min(rem / 20f, 1.0f);
            int a = (int) (alpha * 255);
            int argb = (a << 24) | 0x00FFFFFF;

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer vc = bufferSource.getBuffer(renderLayer);

            vc.vertex(matrix, 0, TEX_SIZE, 0)
                    .color(argb)
                    .texture(0, 1)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(0, 0, 1);

            vc.vertex(matrix, TEX_SIZE, TEX_SIZE, 0)
                    .color(argb)
                    .texture(1, 1)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(0, 0, 1);

            vc.vertex(matrix, TEX_SIZE, 0, 0)
                    .color(argb)
                    .texture(1, 0)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(0, 0, 1);

            vc.vertex(matrix, 0, 0, 0)
                    .color(argb)
                    .texture(0, 0)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(0, 0, 1);

            matrices.pop();
        }

        // Flush all queued draws for these render layers
        bufferSource.draw();
    }
}

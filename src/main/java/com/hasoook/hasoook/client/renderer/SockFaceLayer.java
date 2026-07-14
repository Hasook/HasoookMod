package com.hasoook.hasoook.client.renderer;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.duck.SockFaceAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

import java.util.Random;

/**
 * 袜子糊脸第三人称渲染层。
 * <p>
 * 当玩家被袜子击中脸部时，在玩家头部模型前方渲染袜子纹理，
 * 让其他玩家在第三人称视角也能看到糊脸效果。
 * <p>
 * 每只袜子根据其 packed 数据中的 seed 产生随机的位移和旋转，
 * 多只袜子会以不同角度叠加在脸上。
 */
public class SockFaceLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier[] TEXTURES = {
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay.png"),
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay_1.png"),
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay_2.png"),
    };

    /// 脸部在头部模型前方的 Z 偏移（玩家头部 8×8×8 像素，脸部在前 -Z 方向）
    private static final float FACE_Z = -4.5f / 16.0f;
    /// 脸部中心在头部模型中的 Y 偏移（头部 pivot 在头顶，面部中心在 -4 像素处）
    private static final float FACE_Y = -5.0f / 16.0f;
    /// 每层袜子的 Z 深度偏移，防止多张贴图重叠时产生 z-fighting 闪烁
    private static final float LAYER_Z_OFFSET = 0.001f;

    /// 贴图尺寸（与纹理文件一致）
    private static final float TEX_SIZE = 256.0f;
    /// 脸部贴图在世界空间中的尺寸（约 10 像素 = 10/16 blocks）
    private static final float QUAD_SIZE = 0.625f;

    public SockFaceLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    // ── packed 字段提取（与 SockOverlayHandler 保持一致）──
    private static int remaining(int p)  { return p & 0xFFF; }
    private static int seed(int p)       { return (p >> 12) & 0xF; }
    private static int wearStage(int p)  { return (p >> 16) & 0xFF; }
    private static int texIndex(int p)   { return (p >> 24) & 0xFF; }

    @Override
    public void submit(
            @NonNull PoseStack ps,
            @NonNull SubmitNodeCollector collector,
            int packedLight,
            AvatarRenderState state,
            float limbSwing,
            float limbSwingAmount
    ) {
        if (!(state instanceof SockFaceAccess access)) return;

        String data = access.hasoook$getSockFaceData();
        if (data == null || data.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
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

            // 根据种子生成每只袜子的随机偏移和旋转（多只袜子不重叠）
            Random rand = new Random(seed(packed) * 7919L + ti * 6271L
                    + wearStage(packed) * 13337L + i * 31337L);

            ps.pushPose();

            // 定位到头部模型空间
            this.getParentModel().head.translateAndRotate(ps);

            // 随机微调：±1.5 像素位移 + ±12.5° 旋转
            float jitterX = (rand.nextFloat() - 0.5f) * 3.0f / 16.0f;
            float jitterY = (rand.nextFloat() - 0.5f) * 2.0f / 16.0f;
            float rotZ = (rand.nextFloat() - 0.5f) * 25.0f;

            // 每层向前偏移微小深度，消除 z-fighting
            float layerZ = FACE_Z + i * LAYER_Z_OFFSET;
            ps.translate(jitterX, FACE_Y + jitterY, layerZ);
            ps.mulPose(Axis.ZP.rotationDegrees(rotZ));

            // 缩放到脸部大小
            float s = QUAD_SIZE / TEX_SIZE;
            ps.scale(s, s, 1.0f);

            // 以贴图中心为原点
            ps.translate(-TEX_SIZE / 2.0f, -TEX_SIZE / 2.0f, 0.0f);

            // 渲染带淡入的半透明四边形
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            var renderType = RenderTypes.entityCutoutNoCull(TEXTURES[ti]);
            Matrix4f matrix = ps.last().pose();
            VertexConsumer vc = bufferSource.getBuffer(renderType);

            // 淡入：剩余时间 < 20 tick 时逐渐变透明
            float alpha = Math.min(rem / 20f, 1.0f);
            int a = (int) (alpha * 255);
            int argb = (a << 24) | 0x00FFFFFF;

            vc.addVertex(matrix, 0, TEX_SIZE, 0)
                    .setColor(argb)
                    .setUv(0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            vc.addVertex(matrix, TEX_SIZE, TEX_SIZE, 0)
                    .setColor(argb)
                    .setUv(1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            vc.addVertex(matrix, TEX_SIZE, 0, 0)
                    .setColor(argb)
                    .setUv(1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            vc.addVertex(matrix, 0, 0, 0)
                    .setColor(argb)
                    .setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(0, 0, 1);

            bufferSource.endBatch(renderType);

            ps.popPose();
        }
    }
}

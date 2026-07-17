package com.hasoook.hasoook.entity.client;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.CardProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class CardTrailRenderer {

    private static final double GLOW_HEAD_WIDTH = 0.25;
    private static final double GLOW_TAIL_WIDTH = 0.02;
    private static final double CORE_HEAD_WIDTH = 0.10;
    private static final double CORE_TAIL_WIDTH = 0.002;
    private static final int STUCK_TICKS = 15;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Vector3fc lookF = camera.forwardVector();
        Vector3fc upF = camera.upVector();
        Vector3fc leftF = camera.leftVector();
        Vec3 camLook = new Vec3(lookF.x(), lookF.y(), lookF.z());
        Vec3 camUp = new Vec3(upF.x(), upF.y(), upF.z());
        Vec3 camLeft = new Vec3(leftF.x(), leftF.y(), leftF.z());

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        AABB renderBox = new AABB(camPos, camPos).inflate(128);

        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.lightning());

        for (CardProjectile projectile : mc.level.getEntitiesOfClass(CardProjectile.class, renderBox)) {

            Vec3[] rawTrail = projectile.getTrailPositions();
            if (rawTrail == null || rawTrail.length < 2) continue;

            boolean bomb = projectile.isBomb();
            double yOffset = projectile.getBbHeight() / 2.0;
            int n = rawTrail.length;

            Vec3[] pts = new Vec3[n];
            for (int i = 0; i < n - 1; i++) {
                pts[i] = rawTrail[i].add(0, yOffset, 0);
            }
            pts[n - 1] = projectile.getPosition(pt).add(0, yOffset, 0);

            float fade = 1.0F;
            if (projectile.isStuck()) {
                int age = projectile.getStuckTick();
                if (age >= STUCK_TICKS) continue;
                float p = age / (float) STUCK_TICKS;
                fade = 1.0F - (p * p);
                float shrink = p * p;
                for (int i = 0; i < n - 1; i++) {
                    pts[i] = pts[i].lerp(pts[n - 1],
                            shrink * (float) (n - 1 - i) / (n - 1));
                }
            }

            // 炸弹=红色拖尾, 普通=白蓝色拖尾
            int glowR, glowG, glowB, glowA, coreR, coreG, coreB, coreA, fr, fg, fb, fa;
            if (bomb) {
                glowR=255; glowG=10;  glowB=10;  glowA=140;
                coreR=255; coreG=20;  coreB=20;  coreA=255;
                fr=255;   fg=40;     fb=40;     fa=255;
            } else {
                glowR=255; glowG=255; glowB=255; glowA=30;
                coreR=200; coreG=215; coreB=230; coreA=150;
                fr=255;   fg=255;    fb=255;    fa=180;
            }

            Vec3[] tangents = new Vec3[n];
            for (int i = 0; i < n; i++) {
                if (i == 0) tangents[i] = pts[1].subtract(pts[0]);
                else if (i == n - 1) tangents[i] = pts[n - 1].subtract(pts[n - 2]);
                else tangents[i] = pts[i + 1].subtract(pts[i - 1]);
            }

            Vec3[] rights = new Vec3[n];
            for (int i = 0; i < n; i++) {
                if (tangents[i].lengthSqr() < 0.0001) {
                    rights[i] = camLeft;
                } else {
                    rights[i] = camLook.cross(tangents[i].normalize());
                    if (rights[i].lengthSqr() < 0.001) rights[i] = camLeft;
                    else rights[i] = rights[i].normalize();
                }
            }

            for (int i = 0; i < n - 1; i++) {
                Vec3 segHead = pts[i + 1];
                Vec3 segTail = pts[i];
                Vec3 rHead = rights[i + 1];
                Vec3 rTail = rights[i];
                float headFrac = (float) (i + 1) / (n - 1);
                float tailFrac = (float) i / (n - 1);

                drawSegment(buffer, matrix, segHead, segTail, rHead, rTail,
                        GLOW_HEAD_WIDTH, GLOW_TAIL_WIDTH, headFrac, tailFrac,
                        glowR, glowG, glowB, glowA, fade);
                drawSegment(buffer, matrix, segHead, segTail, rHead, rTail,
                        CORE_HEAD_WIDTH, CORE_TAIL_WIDTH, headFrac, tailFrac,
                        coreR, coreG, coreB, coreA, fade);
            }

            drawFlare(buffer, matrix, pts[n - 1], camUp, camLeft,
                    CORE_HEAD_WIDTH * 2.0 * fade,
                    fr, fg, fb, (int) (fa * fade));
        }

        bufferSource.endBatch(RenderTypes.lightning());
        poseStack.popPose();
    }

    private static void drawSegment(VertexConsumer b, Matrix4f m,
                                     Vec3 head, Vec3 tail, Vec3 rHead, Vec3 rTail,
                                     double headW, double tailW,
                                     float headFrac, float tailFrac,
                                     int baseR, int baseG, int baseB, int baseAlpha,
                                     float fade) {
        double hw = tailW + (headW - tailW) * headFrac;
        double tw = tailW + (headW - tailW) * tailFrac;
        int ha = (int) (baseAlpha * fade * headFrac);
        int ta = (int) (baseAlpha * fade * tailFrac);
        Vec3 hl = head.add(rHead.scale(hw * fade));
        Vec3 hr = head.subtract(rHead.scale(hw * fade));
        Vec3 tl = tail.add(rTail.scale(tw * fade));
        Vec3 tr = tail.subtract(rTail.scale(tw * fade));
        v(b, m, hl, baseR, baseG, baseB, ha);
        v(b, m, hr, baseR, baseG, baseB, ha);
        v(b, m, tr, baseR, baseG, baseB, ta);
        v(b, m, tl, baseR, baseG, baseB, ta);
    }

    private static void drawFlare(VertexConsumer b, Matrix4f m, Vec3 center,
                                  Vec3 up, Vec3 left,
                                  double radius, int r, int g, int colorB, int a) {
        v(b, m, center.add(up.scale(radius)), r, g, colorB, a);
        v(b, m, center.subtract(left.scale(radius)), r, g, colorB, a);
        v(b, m, center.subtract(up.scale(radius)), r, g, colorB, a);
        v(b, m, center.add(left.scale(radius)), r, g, colorB, a);
    }

    private static void v(VertexConsumer c, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        c.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    private CardTrailRenderer() {}
}

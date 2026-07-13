package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.duck.HeadRemovedAccess;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

/**
 * 在模型实际渲染时隐藏头部部件。
 *
 * Minecraft 1.21.1 的渲染流程分为两个阶段：
 * <ol>
 *   <li><b>提交阶段</b>：submitModel() 只是把模型加入渲染队列</li>
 *   <li><b>渲染阶段</b>：ModelFeatureRenderer.renderModel() 真正调用 model.renderToBuffer()</li>
 * </ol>
 *
 * 之前基于 RenderLivingEvent.Pre/Post 的方案失败，是因为它们在提交阶段修改
 * 模型可见性，但可见性在 Post 事件中被恢复，而渲染阶段尚未开始。
 *
 * 正确的注入点是 renderModel()，这里同时拥有 Model 和 RenderState，
 * 而且在 renderToBuffer() 调用前修改 visible 会立即生效。
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

    @Unique
    private static final Map<Model<?>, List<ModelPart>> HEAD_PARTS_CACHE = new HashMap<>();

    /** 渲染模型前隐藏头部 */
    @Inject(
        method = "renderModel(Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
        at = @At("HEAD")
    )
    private void hideHeadBeforeModelRender(
        SubmitNodeStorage.ModelSubmit<?> modelSubmit,
        RenderType renderType,
        VertexConsumer consumer,
        OutlineBufferSource outlineSource,
        MultiBufferSource.BufferSource crumblingSource,
        CallbackInfo ci
    ) {
        Object state = modelSubmit.state();
        if (!(state instanceof HeadRemovedAccess access)) {
            return;
        }
        // 隐藏头部：被剪头 或 有移植头（原始头部已被移除）
        if (!access.hasoook$isHeadRemoved()
                && (access.hasoook$getTransplantedHeadType() == null
                    || access.hasoook$getTransplantedHeadType().isEmpty())) {
            return;
        }

        Model<?> model = modelSubmit.model();
        List<ModelPart> headParts = getOrCacheHeadParts(model);
        if (headParts.isEmpty()) return;

        for (ModelPart part : headParts) {
            part.visible = false;
        }
    }

    /** 渲染模型后恢复头部 */
    @Inject(
        method = "renderModel(Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/OutlineBufferSource;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
        at = @At("RETURN")
    )
    private void showHeadAfterModelRender(
        SubmitNodeStorage.ModelSubmit<?> modelSubmit,
        RenderType renderType,
        VertexConsumer consumer,
        OutlineBufferSource outlineSource,
        MultiBufferSource.BufferSource crumblingSource,
        CallbackInfo ci
    ) {
        Object state = modelSubmit.state();
        if (!(state instanceof HeadRemovedAccess access)) {
            return;
        }
        // 隐藏头部：被剪头 或 有移植头（原始头部已被移除）
        if (!access.hasoook$isHeadRemoved()
                && (access.hasoook$getTransplantedHeadType() == null
                    || access.hasoook$getTransplantedHeadType().isEmpty())) {
            return;
        }

        Model<?> model = modelSubmit.model();
        List<ModelPart> headParts = HEAD_PARTS_CACHE.get(model);
        if (headParts != null) {
            for (ModelPart part : headParts) {
                part.visible = true;
            }
        }
    }

    /**
     * 从缓存获取或计算模型的头部部件列表。
     * 遍历模型树，找出所有与头部相关的 ModelPart 实例。
     */
    @Unique
    private static List<ModelPart> getOrCacheHeadParts(Model<?> model) {
        return HEAD_PARTS_CACHE.computeIfAbsent(model, m -> {
            List<ModelPart> parts = new ArrayList<>();
            collectHeadParts(m.root(), parts);
            // ★ 兜底：没有 head → 尝试 body（鱿鱼/恶魂直接在 root 下，蜜蜂在 root→bone 下）
            if (parts.isEmpty()) {
                if (m.root().hasChild("body")) {
                    parts.add(m.root().getChild("body"));
                } else {
                    for (Map.Entry<String, ModelPart> entry : m.root().children.entrySet()) {
                        if (entry.getKey().toLowerCase().contains("head")) continue;
                        if (entry.getValue().hasChild("body")) {
                            parts.add(entry.getValue().getChild("body"));
                            break;
                        }
                    }
                }
            }
            return parts;
        });
    }

    /**
     * 递归收集头部部件。
     *
     * 匹配规则（忽略大小写）：
     * <ul>
     *   <li>名称包含 "head"（排除纯容器 "head_parts"）</li>
     *   <li>名称精确匹配 "hat"、"headwear"、"helmet"</li>
     * </ul>
     *
     * 特殊处理：
     * <ul>
     *   <li>"head_parts"（马等）：不直接隐藏容器，继续深入找真正的 "head" 部件</li>
     *   <li>"center_head"/"right_head"/"left_head"（凋灵）：三个头全部隐藏</li>
     *   <li>设置 visible=false 会连带隐藏该部件的所有子部件</li>
     * </ul>
     */
    @Unique
    private static void collectHeadParts(ModelPart part, List<ModelPart> out) {
        for (Map.Entry<String, ModelPart> entry : part.children.entrySet()) {
            String name = entry.getKey().toLowerCase();
            ModelPart child = entry.getValue();

            // "head_parts" 是马的头部组件容器 → 隐藏整个容器（含 head + neck + ears）
            if (name.equals("head_parts")) {
                out.add(child);
                continue;
            }

            if (name.contains("head") || name.equals("hat") || name.equals("headwear") || name.equals("helmet")) {
                out.add(child);
                // 不递归进入 head 子部件 — 设置 visible=false 已足够
            } else {
                // 继续深入搜索（例如 Warden: bone→body→head, Ravager: neck→head）
                collectHeadParts(child, out);
            }
        }
    }
}

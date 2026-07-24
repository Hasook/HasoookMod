package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.duck.CopperArrowCountAccess;
import com.hasoook.hasoook.duck.EntityIdAccess;
import com.hasoook.hasoook.duck.HeadRemovedAccess;
import com.hasoook.hasoook.duck.SockFaceAccess;
import com.hasoook.hasoook.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    // 冻结摔下去那一刻的朝向，之后不再变动
    private static final Map<UUID, Float> FROZEN_BODY_ROT = new HashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void hasoook$syncHeadRemoved(LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        if (renderState instanceof HeadRemovedAccess access) {
            // 获取实体的无头状态并存入渲染状态
            boolean headRemoved = Boolean.TRUE.equals(entity.getData(ModAttachments.HEAD_REMOVED.get()));
            access.hasoook$setHeadRemoved(headRemoved);

            // 同步移植头部类型
            String transplantedType = entity.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
            access.hasoook$setTransplantedHeadType(transplantedType != null && !transplantedType.isEmpty() ? transplantedType : null);

            // 同步移植玩家头 UUID（用于皮肤查询）
            String playerUuid = entity.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get());
            access.hasoook$setTransplantedPlayerUuid(playerUuid != null && !playerUuid.isEmpty() ? playerUuid : null);

            // 同步移植玩家头名称（用于皮肤查询）
            String playerName = entity.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get());
            access.hasoook$setTransplantedPlayerName(playerName != null && !playerName.isEmpty() ? playerName : null);
        }

        // 同步袜子糊脸数据
        if (renderState instanceof SockFaceAccess sockAccess) {
            String sockFaceData = entity.getData(ModAttachments.SOCK_FACE.get());
            sockAccess.hasoook$setSockFaceData(sockFaceData != null ? sockFaceData : "");
        }

        // 同步铜箭卡身数量 — 用于 StuckCopperArrowLayer / CopperArrowStuckLayer 渲染铜箭贴图
        if (renderState instanceof CopperArrowCountAccess coppperAccess) {
            int copperCount = entity.getData(ModAttachments.COPPER_ARROW_COUNT.get());
            coppperAccess.hasoook$setCopperArrowCount(copperCount);
        }

        // 同步实体网络 ID — 用于 StuckCopperArrowLayer 生成稳定的随机种子
        if (renderState instanceof EntityIdAccess idAccess) {
            idAccess.hasoook$setEntityId(entity.getId());
        }

        // 一级伤残：冻结倒下瞬间的朝向，之后不再旋转
        if (entity.hasEffect(ModEffects.DISABILITY)
                && renderState.hasPose(Pose.SLEEPING)
                && renderState.bedOrientation == null) {
            UUID uuid = entity.getUUID();
            Float frozen = FROZEN_BODY_ROT.get(uuid);
            if (frozen == null) {
                frozen = renderState.bodyRot; // 记住摔下去那一刻的朝向
                FROZEN_BODY_ROT.put(uuid, frozen);
            }
            renderState.bodyRot = frozen;
        } else {
            // 不在伤残状态 → 清理
            FROZEN_BODY_ROT.remove(entity.getUUID());
        }
    }

    /**
     * 一级伤残身体居中。
     *
     * SLEEPING 旋转后当前 Y 轴恒指北 → -Y = 南，平移 H/2 让身体归位。
     */
    @Inject(
        method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
        at = @At("RETURN")
    )
    private void centerSleepingBody(LivingEntityRenderState renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        if (!renderState.hasPose(Pose.SLEEPING)) return;
        if (renderState.bedOrientation != null) return;

        float standingHeight = renderState.entityType.getDimensions().height() * renderState.scale;
        float offset = standingHeight / 2.0F;
        poseStack.translate(0, -offset, 0);
    }
}

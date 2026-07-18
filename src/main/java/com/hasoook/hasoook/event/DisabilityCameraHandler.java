package com.hasoook.hasoook.event;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 玩家受到致残效果时自动切换到第三人称视角（背面），
 * 效果结束后恢复原来的视角类型。
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class DisabilityCameraHandler {

    private static CameraType previousCameraType = null;
    private static boolean wasDisabled = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isDisabled = mc.player.hasEffect(ModEffects.DISABILITY);

        if (isDisabled) {
            // 强制取消潜行
            if (mc.player.isShiftKeyDown()) {
                mc.player.setShiftKeyDown(false);
            }

            if (!wasDisabled) {
                // 刚获得致残 → 记录当前视角并切换到第三人称背面
                previousCameraType = mc.options.getCameraType();
                if (previousCameraType == CameraType.FIRST_PERSON) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
        } else if (!isDisabled && wasDisabled) {
            // 致残结束 → 恢复
            if (previousCameraType != null) {
                mc.options.setCameraType(previousCameraType);
                previousCameraType = null;
            }
        }

        wasDisabled = isDisabled;
    }
}

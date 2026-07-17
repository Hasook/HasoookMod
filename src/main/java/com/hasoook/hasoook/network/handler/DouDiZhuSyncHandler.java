package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.network.payload.DouDiZhuSyncPayload;
import com.hasoook.hasoook.screen.custom.DouDiZhuGameScreen;
import net.minecraft.client.Minecraft;

/**
 * 客户端：接收斗地主游戏状态同步
 */
public class DouDiZhuSyncHandler {
    public static void handle(DouDiZhuSyncPayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof DouDiZhuGameScreen screen) {
                screen.updateGameState(payload);
            }
        });
    }
}

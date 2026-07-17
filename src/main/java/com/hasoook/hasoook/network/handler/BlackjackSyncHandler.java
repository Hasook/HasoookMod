package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.network.payload.BlackjackSyncPayload;
import com.hasoook.hasoook.screen.custom.BlackjackGameScreen;
import net.minecraft.client.Minecraft;

public class BlackjackSyncHandler {
    public static void handle(BlackjackSyncPayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof BlackjackGameScreen screen) {
                screen.updateGameState(payload);
            }
        });
    }
}

package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.screen.custom.PaintProcessor;
import com.hasoook.hasoook.network.payload.QuestSyncPayload;
import net.minecraft.client.Minecraft;

public class QuestSyncHandler {
    public static void handle(QuestSyncPayload payload) {
        Minecraft.getInstance().execute(() -> {
            PaintProcessor.setActiveQuestFromServer(
                    payload.questId(),
                    payload.questName(),
                    payload.isEntity()
            );
        });
    }
}
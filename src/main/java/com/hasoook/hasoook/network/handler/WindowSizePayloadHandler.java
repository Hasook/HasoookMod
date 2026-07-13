package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.network.manager.PlayerWindowManager;
import com.hasoook.hasoook.network.payload.WindowSizePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WindowSizePayloadHandler {
    public static void handle(WindowSizePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();

            int area = payload.width() * payload.height();

            PlayerWindowManager.AREA_MAP.put(player.getUUID(), area);
        });
    }
}

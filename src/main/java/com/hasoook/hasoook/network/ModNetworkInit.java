package com.hasoook.hasoook.network;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.component.SockFaceData;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.SocksItem;
import com.hasoook.hasoook.network.payload.RemoveSockPayload;
import com.hasoook.hasoook.network.payload.SockFaceSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class ModNetworkInit {

    public static void initialize() {
        // ── 注册 payload 类型 ──
        PayloadTypeRegistry.playC2S().register(RemoveSockPayload.ID, RemoveSockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SockFaceSyncPayload.ID, SockFaceSyncPayload.CODEC);

        // ── 服务端接收：移除糊脸袜子 ──
        ServerPlayNetworking.registerGlobalReceiver(RemoveSockPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    context.server().execute(() -> {
                        String data = SockFaceData.getSockFace(player);
                        if (data.isEmpty()) return;
                        String[] parts = data.split(",");
                        if (payload.index() < 0 || payload.index() >= parts.length) return;

                        int removedPacked = Integer.parseInt(parts[payload.index()]);
                        int wearStage = (removedPacked >> 16) & 0xFF;

                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length; i++) {
                            if (i == payload.index()) continue;
                            int packed = Integer.parseInt(parts[i]);
                            if ((packed & 0xFFF) <= 0) continue;
                            if (!sb.isEmpty()) sb.append(',');
                            sb.append(packed);
                        }
                        SockFaceData.setSockFace(player, sb.toString());

                        syncSockFaceToPlayer(player);

                        ItemStack sock = new ItemStack(ModItems.SOCK);
                        if (wearStage > 0) {
                            int minWear = SocksItem.getMinWearForStage(wearStage);
                            sock.set(ModDataComponents.SOCKS_WEAR, minWear);
                        }
                        if (!player.getInventory().insertStack(sock)) {
                            player.dropItem(sock, false);
                        }
                    });
                });
    }

    public static void syncSockFaceToPlayer(ServerPlayerEntity player) {
        String data = SockFaceData.getSockFace(player);
        ServerPlayNetworking.send(player, new SockFaceSyncPayload(player.getUuid(), data));
    }
}

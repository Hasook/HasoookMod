package com.hasoook.hasoook.network;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.ArmorStandSwordItem;
import com.hasoook.hasoook.item.custom.SocksItem;
import com.hasoook.hasoook.network.handler.ConsumeDyeHandler;
import com.hasoook.hasoook.network.handler.DrawSaveMapHandler;
import com.hasoook.hasoook.network.handler.GiveResultHandler;
import com.hasoook.hasoook.network.handler.QuestSyncHandler;
import com.hasoook.hasoook.network.manager.PlayerFpsManager;
import com.hasoook.hasoook.network.manager.PlayerWindowManager;
import com.hasoook.hasoook.network.payload.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class ModNetworkInit {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {

        final PayloadRegistrar registrar = event.registrar(Hasoook.MOD_ID);

        registrar.playToServer(
                FpsPayload.TYPE,
                FpsPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        PlayerFpsManager.FPS_MAP.put(player.getUUID(), payload.fps());
                    });
                }
        );
        registrar.playToServer(
                WindowSizePayload.TYPE,
                WindowSizePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        int area = payload.width() * payload.height();
                        PlayerWindowManager.AREA_MAP.put(player.getUUID(), area);
                    });
                }
        );
        registrar.playToServer(
                ConsumeDyePayload.TYPE,
                ConsumeDyePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        ConsumeDyeHandler.handle(player, payload);
                    });
                }
        );
        registrar.playToServer(
                GiveResultPayload.TYPE,
                GiveResultPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        GiveResultHandler.handle(player, payload);
                    });
                }
        );
        registrar.playToClient(
                QuestSyncPayload.TYPE,
                QuestSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    QuestSyncHandler.handle(payload);
                })
        );
        registrar.playToServer(
                DrawSaveMapPayload.TYPE,
                DrawSaveMapPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        DrawSaveMapHandler.handle(player, payload);
                    });
                }
        );
        registrar.playToServer(
                SelectSwordSlotPayload.TYPE,
                SelectSwordSlotPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        ItemStack stack = player.containerMenu.getSlot(payload.containerSlotId()).getItem();
                        if (stack.getItem() instanceof ArmorStandSwordItem) {
                            ArmorStandSwordItem.applySelectedSlot(stack, payload.selectedSlot());
                        }
                    });
                }
        );
        registrar.playToServer(
                RemoveSockPayload.TYPE,
                RemoveSockPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        String data = player.getData(ModAttachments.SOCK_FACE.get());
                        if (data.isEmpty()) return;
                        String[] parts = data.split(",");
                        if (payload.index() < 0 || payload.index() >= parts.length) return;

                        // 解析被移除的袜子，确定臭味等级
                        int removedPacked = Integer.parseInt(parts[payload.index()]);
                        int wearStage = (removedPacked >> 16) & 0xFF;

                        // 重建列表（不含被移除项）
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length; i++) {
                            if (i == payload.index()) continue;
                            int packed = Integer.parseInt(parts[i]);
                            if ((packed & 0xFFF) <= 0) continue;
                            if (!sb.isEmpty()) sb.append(',');
                            sb.append(packed);
                        }
                        player.setData(ModAttachments.SOCK_FACE.get(), sb.toString());

                        // 返还袜子物品（带臭味数据）
                        ItemStack sock = new ItemStack(ModItems.SOCK.get());
                        if (wearStage > 0) {
                            int minWear = SocksItem.getMinWearForStage(wearStage);
                            sock.set(ModDataComponents.SOCKS_WEAR.get(), minWear);
                        }
                        if (!player.getInventory().add(sock)) {
                            player.spawnAtLocation(player.level(), sock);
                        }
                    });
                }
        );
    }
}

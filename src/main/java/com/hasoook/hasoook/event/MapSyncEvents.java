package com.hasoook.hasoook.event;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.Hasoook;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * 展示型地图：同步手持地图数据给其他玩家。
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.DEDICATED_SERVER)
public class MapSyncEvents {
    // 记录玩家上次手持的地图
    private static final Map<UUID, MapId> lastMapId = new HashMap<>();
    // 记录已向哪些玩家发送过完整数据
    private static final Map<UUID, Set<UUID>> sentFullData = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!Config.PAQ_DISPLAY_MAP.get()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack mainHand = player.getMainHandItem();
        // 非地图时清除记录
        if (!mainHand.is(Items.FILLED_MAP)) {
            lastMapId.remove(player.getUUID());
            sentFullData.remove(player.getUUID());
            return;
        }

        MapId mapId = mainHand.get(DataComponents.MAP_ID);
        if (mapId == null) return;

        MapItemSavedData mapData = player.level().getMapData(mapId);
        if (mapData == null) return;

        lastMapId.put(player.getUUID(), mapId);

        // 获取同维度其他玩家
        List<ServerPlayer> otherPlayers = new ArrayList<>(player.level().players());
        otherPlayers.remove(player);
        if (otherPlayers.isEmpty()) return;

        Set<UUID> alreadySent = sentFullData.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

        for (ServerPlayer other : otherPlayers) {
            // 避免重复发送完整数据
            if (!alreadySent.contains(other.getUUID())) {
                // 复制装饰列表
                List<MapDecoration> decorations = new ArrayList<>();
                mapData.getDecorations().forEach(decorations::add);
                // 创建完整颜色补丁
                MapItemSavedData.MapPatch colorPatch = new MapItemSavedData.MapPatch(0, 0, 128, 128, mapData.colors);

                // 发送完整地图数据包
                ClientboundMapItemDataPacket fullPacket = new ClientboundMapItemDataPacket(
                        mapId,
                        mapData.scale,
                        mapData.locked,
                        decorations.isEmpty() ? Optional.empty() : Optional.of(decorations),
                        Optional.of(colorPatch)
                );
                other.connection.send(fullPacket);
                alreadySent.add(other.getUUID());
            }
        }
    }
}
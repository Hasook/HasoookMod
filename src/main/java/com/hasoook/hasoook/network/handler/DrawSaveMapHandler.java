package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.network.payload.DrawSaveMapPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class DrawSaveMapHandler {
    public static void handle(ServerPlayer player, DrawSaveMapPayload payload) {
        ServerLevel level = player.level();
        int[] rawPixels = payload.pixels();

        // 获取空闲地图 ID
        MapId mapId = level.getFreeMapId();

        // 创建锁定状态的地图数据，防止被原版地形扫描覆盖
        MapItemSavedData tempMapData = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension());
        MapItemSavedData mapData = tempMapData.locked();

        // 将 64x64 画作放大到 128x128 地图
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = rawPixels[y * 64 + x];
                byte mapColorByte = getMapColorByte(argb);

                int mapX = x * 2;
                int mapY = y * 2;
                mapData.colors[mapY * 128 + mapX] = mapColorByte;
                mapData.colors[mapY * 128 + mapX + 1] = mapColorByte;
                mapData.colors[(mapY + 1) * 128 + mapX] = mapColorByte;
                mapData.colors[(mapY + 1) * 128 + mapX + 1] = mapColorByte;
            }
        }

        // 标记修改并保存地图数据
        mapData.setDirty();
        level.setMapData(mapId, mapData);

        // 生成地图物品，标题根据是否携带作品名动态变化
        ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
        mapStack.set(DataComponents.MAP_ID, mapId);

        String playerName = player.getName().getString();
        String itemName = payload.itemName();
        String title;
        if (itemName != null && !itemName.isBlank()) {
            title = "§e" + playerName + "的画作（" + itemName + "）";
        } else {
            title = "§e" + playerName + "的画作";
        }
        mapStack.set(DataComponents.CUSTOM_NAME, Component.literal(title));

        // 交给玩家，背包满则掉落
        if (!player.getInventory().add(mapStack)) {
            player.drop(mapStack, false);
        }
    }

    private static byte getMapColorByte(int argb) {
        // 透明像素返回 0，使用地图默认底色
        if ((argb >>> 24) == 0) return 0;

        DyeColor dye = switch(argb) {
            case 0xFF1D1D21 -> DyeColor.BLACK;
            case 0xFFF9FFFE -> DyeColor.WHITE;
            case 0xFF474F52 -> DyeColor.GRAY;
            case 0xFF9D9D97 -> DyeColor.LIGHT_GRAY;
            case 0xFF835432 -> DyeColor.BROWN;
            case 0xFFB02E26 -> DyeColor.RED;
            case 0xFFF9801D -> DyeColor.ORANGE;
            case 0xFFFED83D -> DyeColor.YELLOW;
            case 0xFF5E7C16 -> DyeColor.GREEN;
            case 0xFF80C71F -> DyeColor.LIME;
            case 0xFF169C9C -> DyeColor.CYAN;
            case 0xFF3AB3DA -> DyeColor.LIGHT_BLUE;
            case 0xFF3C44AA -> DyeColor.BLUE;
            case 0xFF8932B8 -> DyeColor.PURPLE;
            case 0xFFC74EBD -> DyeColor.MAGENTA;
            case 0xFFF38BAA -> DyeColor.PINK;
            default -> DyeColor.WHITE;
        };

        // 基础颜色
        return (byte) (dye.getMapColor().id * 4 + 2);
    }
}
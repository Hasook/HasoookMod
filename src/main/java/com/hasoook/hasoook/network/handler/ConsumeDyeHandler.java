package com.hasoook.hasoook.network.handler;

import com.hasoook.hasoook.network.payload.ConsumeDyePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ConsumeDyeHandler {
    public static void handle(ServerPlayer player, ConsumeDyePayload payload) {
        if (player.isCreative()) return;

        for (String idStr : payload.itemIds()) {
            Identifier id = Identifier.tryParse(idStr);
            if (id == null) continue;

            Item itemToConsume = BuiltInRegistries.ITEM.getValue(id);

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(itemToConsume)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        player.getInventory().setChanged();
    }
}
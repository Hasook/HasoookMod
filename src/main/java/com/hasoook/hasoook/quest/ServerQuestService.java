package com.hasoook.hasoook.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ServerQuestService {
    public static void assignRandomItemQuest(ServerPlayer player) {
        List<String> keys = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            keys.add(id.toString());
        }
        if (keys.isEmpty()) return;

        String questId = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        String questName = getItemDisplayName(questId);

        PlayerQuestManager.setQuest(player, questId, questName, false);
        QuestSyncUtil.sync(player);
    }

    private static String getItemDisplayName(String itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId));
            return new ItemStack(item).getHoverName().getString();
        } catch (Exception e) {
            return itemId;
        }
    }
}
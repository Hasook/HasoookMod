package com.hasoook.hasoook.quest;

import com.hasoook.hasoook.network.payload.QuestSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class QuestSyncUtil {
    public static void sync(ServerPlayer player) {
        String id = PlayerQuestManager.getQuestId(player);
        String name = PlayerQuestManager.getQuestName(player);
        boolean isEntity = PlayerQuestManager.isQuestEntity(player);

        PacketDistributor.sendToPlayer(player, new QuestSyncPayload(
                id == null ? "" : id,
                name == null ? "" : name,
                isEntity
        ));
    }

    public static void clearAndSync(ServerPlayer player) {
        PlayerQuestManager.clearQuest(player);
        PacketDistributor.sendToPlayer(player, new QuestSyncPayload("", "", false));
    }
}
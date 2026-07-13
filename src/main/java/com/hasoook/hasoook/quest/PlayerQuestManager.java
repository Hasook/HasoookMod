package com.hasoook.hasoook.quest;

import net.minecraft.server.level.ServerPlayer;

public class PlayerQuestManager {
    private static final String KEY_ID = "hasoook_active_quest_id";
    private static final String KEY_NAME = "hasoook_active_quest_name";
    private static final String KEY_IS_ENTITY = "hasoook_active_quest_is_entity";

    public static void setQuest(ServerPlayer player, String id, String name, boolean isEntity) {
        var tag = player.getPersistentData();
        tag.putString(KEY_ID, id == null ? "" : id);
        tag.putString(KEY_NAME, name == null ? "" : name);
        tag.putBoolean(KEY_IS_ENTITY, isEntity);
    }

    public static void clearQuest(ServerPlayer player) {
        var tag = player.getPersistentData();
        tag.remove(KEY_ID);
        tag.remove(KEY_NAME);
        tag.remove(KEY_IS_ENTITY);
    }

    public static boolean hasQuest(ServerPlayer player) {
        String id = getQuestId(player);
        return !id.isBlank();
    }

    public static String getQuestId(ServerPlayer player) {
        return player.getPersistentData().getString(KEY_ID).orElse("");
    }

    public static String getQuestName(ServerPlayer player) {
        return player.getPersistentData().getString(KEY_NAME).orElse("");
    }

    public static boolean isQuestEntity(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_IS_ENTITY).orElse(false);
    }
}
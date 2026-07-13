package com.hasoook.hasoook.event;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.quest.QuestSyncUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class QuestPlayerEvents {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level().getClass().getSimpleName().contains("ReplayServer")) {
                return;
            }
            QuestSyncUtil.sync(player);
        }
    }
}
package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.custom.RecoveryClockItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class RecoveryClockEvent {
    /**
     * 每tick检测玩家是否仍然持有追溯时钟
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 如果玩家没有在使用时钟
        if (!RecoveryClockItem.isPlayerRewinding(player)
                && !RecoveryClockItem.isPlayerRecording(player)) {
            return;
        }

        boolean hasClock = player.getInventory().contains(stack ->
                stack.getItem() instanceof RecoveryClockItem
        );

        if (!hasClock) {
            RecoveryClockItem.forceStop(serverPlayer);
        }
    }

    // 玩家死亡时清除追溯数据
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            RecoveryClockItem.clearPlayerData(player.getUUID());
        }
    }
}

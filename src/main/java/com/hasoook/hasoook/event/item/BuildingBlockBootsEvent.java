package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 积木鞋事件 —— tooltip 显示数量 + 左键挥动甩出积木
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class BuildingBlockBootsEvent {

    private static final int MAX_BLOCKS = 16;
    private static final float EXTRACT_CHANCE = 0.3F;
    private static final int MAX_SOUND_PLAYS = 5;
    private static final SoundEvent[] RATTLE_SOUNDS = {
            SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_TRAPDOOR_OPEN
    };

    private static final Map<UUID, Boolean> WAS_SWINGING = new HashMap<>();
    private static final Map<UUID, Integer> SOUND_PLAYS_LEFT = new HashMap<>();
    private static final Map<UUID, Integer> SOUND_DELAY = new HashMap<>();

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int count = stack.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);
        if (count > 0) {
            event.getToolTip().add(
                    Component.translatable("tooltip.hasoook.building_block_attached", count, MAX_BLOCKS)
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();

        // ── 延时音效播放 ──
        Integer playsLeft = SOUND_PLAYS_LEFT.getOrDefault(uuid, 0);
        if (playsLeft > 0) {
            int delay = SOUND_DELAY.getOrDefault(uuid, 0) - 1;
            if (delay <= 0) {
                float pitch = 0.7F + player.getRandom().nextFloat() * 0.6F;
                SoundEvent sound = RATTLE_SOUNDS[player.getRandom().nextInt(RATTLE_SOUNDS.length)];
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        sound, SoundSource.PLAYERS, 0.6F, pitch);
                playsLeft--;
                SOUND_PLAYS_LEFT.put(uuid, playsLeft);
                // 间隔随机 1~2 tick，制造不规则感
                SOUND_DELAY.put(uuid, player.getRandom().nextInt(2) + 1);
            } else {
                SOUND_DELAY.put(uuid, delay);
            }
        }

        // ── 挥动上升沿检测 ──
        boolean swinging = player.swinging;
        boolean wasSwinging = WAS_SWINGING.getOrDefault(uuid, false);
        WAS_SWINGING.put(uuid, swinging);

        if (!swinging || wasSwinging) return;

        ItemStack stack = player.getMainHandItem();
        int count = stack.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);
        if (count <= 0) return;

        // ── 先算甩出量 ──
        int x = 0;
        if (player.getRandom().nextFloat() < EXTRACT_CHANCE) {
            x = player.getRandom().nextInt(count) + 1;
        }

        // 剩余数量 = 当前 - 甩出
        int remaining = count - x;

        // ── 根据剩余数量播放碰撞音效（0 个就不出声）──
        if (remaining > 0) {
            int plays = Math.min(remaining, MAX_SOUND_PLAYS);
            SOUND_PLAYS_LEFT.put(uuid, plays);
            SOUND_DELAY.put(uuid, 0);
        }

        // ── 没触发甩出就直接返回 ──
        if (x == 0) return;

        // ── 执行甩出 ──
        if (remaining <= 0) {
            stack.remove(ModDataComponents.BUILDING_BLOCK_ATTACHED.get());
        } else {
            stack.set(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), remaining);
        }

        // 朝玩家面向的方向扔出积木
        ItemStack drop = new ItemStack(ModItems.BUILDING_BLOCK.get(), x);
        player.drop(drop, false);

        // 甩出音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.5F);
    }
}

package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber
public class EchoBottleEvent {
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;

        DamageSource source = event.getSource();

        if (!source.is(DamageTypes.SONIC_BOOM)) {
            return;
        }

        // 是否正在使用物品
        if (!player.isUsingItem()) {
            return;
        }

        ItemStack itemStack = player.getUseItem();

        if (itemStack.is(ModItems.ECHO_BOTTLE.get())) {
            itemStack.shrink(1); // 消耗一个物品
            player.getInventory().add(new ItemStack(ModItems.SONIC_BOOM_BOTTLE.get())); // 给予一个音波瓶
            player.stopUsingItem(); // 停止使用物品

            event.setNewDamage(0);
        }
    }

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        handleSound(event.getLevel(), event.getSound(), event.getPosition());
    }

    @SubscribeEvent
    public static void onSoundEntity(PlayLevelSoundEvent.AtEntity event) {
        handleSound(event.getLevel(), event.getSound(), event.getEntity().position());
    }

    private static void handleSound(Level level, Holder<SoundEvent> soundHolder, Vec3 pos) {
        if (soundHolder == null) return;

        // 重要：确保只在服务端写入数据，避免客户端和服务器数据不同步（Desync）
        if (level.isClientSide()) return;

        // 安全地获取声音的 Identifier
        soundHolder.unwrapKey().ifPresent(resourceKey -> {
            Identifier soundId = resourceKey.identifier();

            for (Player player : level.players()) {
                if (!player.isUsingItem()) continue;

                ItemStack stack = player.getUseItem();
                if (!stack.is(ModItems.ECHO_BOTTLE.get())) continue;

                // 距离限制
                if (player.distanceToSqr(pos) > 8 * 8) continue;

                // 写入声音ID到DataComponent
                stack.set(ModDataComponents.RECORDED_SOUND.get(), soundId.toString());

                if (player instanceof ServerPlayer serverPlayer) {
                    giveAdvancement(serverPlayer);// 授予进度
                }
            }
        });
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();

        // 必须在服务端处理数据
        if (level.isClientSide()) return;

        // 获取爆炸的中心坐标
        Vec3 pos = event.getExplosion().center();

        // 原版默认的爆炸声音 ID
        String explodeSoundId = "minecraft:entity.generic.explode";

        for (Player player : level.players()) {
            if (!player.isUsingItem()) continue;

            ItemStack stack = player.getUseItem();
            if (!stack.is(ModItems.ECHO_BOTTLE.get())) continue;

            if (player.distanceToSqr(pos) > 16 * 16) continue;

            // 写入爆炸声音
            stack.set(ModDataComponents.RECORDED_SOUND.get(), explodeSoundId);

            if (player instanceof ServerPlayer serverPlayer) {
                giveAdvancement(serverPlayer);// 授予进度
            }
        }
    }

    public static void giveAdvancement(ServerPlayer serverPlayer) {
        MinecraftServer server = serverPlayer.level().getServer();
        AdvancementHolder adv = server.getAdvancements().get(ResourceKey.create(Registries.ADVANCEMENT, Hasoook.id("record_button")).identifier());

        if (adv == null) return;

        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(adv);

        if (progress.isDone()) return;

        for (String criterion : progress.getRemainingCriteria()) {
            serverPlayer.getAdvancements().award(adv, criterion);
        }
    }
}
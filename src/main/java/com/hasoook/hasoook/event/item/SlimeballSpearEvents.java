package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;

import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.resources.ResourceKey;

import static com.hasoook.hasoook.item.custom.SlimeSpearItem.getStoredItem;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class SlimeballSpearEvents {
    private static final ResourceKey<net.minecraft.advancements.Advancement> SELF_CONTRADICTION = ResourceKey.create(Registries.ADVANCEMENT, Hasoook.id("self_contradiction"));

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack result = event.getCrafting();
        ItemStack storedItem = getStoredItem(result);

        if (!storedItem.is(Items.SHIELD)) return;

        MinecraftServer server = player.level().getServer();
        AdvancementHolder adv = server.getAdvancements().get(SELF_CONTRADICTION.identifier());

        if (adv == null) return;

        AdvancementProgress progress =
                player.getAdvancements().getOrStartProgress(adv);

        if (progress.isDone()) return;

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    @SubscribeEvent
    public static void livingDamage(LivingDamageEvent.Pre event) {
        // 只在服务端处理
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        ItemStack itemStack = event.getSource().getWeaponItem();

        if (itemStack != null) {
            ItemStack storedItem = SlimeSpearItem.getStoredItem(itemStack);
            Entity entity = event.getEntity();

            if (storedItem.is(Items.SHIELD)) {
                event.setNewDamage(0.0F);
                serverLevel.playSound(null, entity.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }
}


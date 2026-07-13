package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class BucketEvent {
    private static final int MAX_PROGRESS = 100;
    private static final int BAR_LENGTH = 10;

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BucketItem bucketItem)) return;
        if (bucketItem.getContent() != Fluids.EMPTY) return;

        String lastFluid = stack.get(ModDataComponents.LAST_FLUID.get());
        if (lastFluid == null) return;

        if (ModEnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) <= 0) return;

        event.getToolTip().add(Component.empty());

        int progress = stack.getOrDefault(ModDataComponents.PROGRESS.get(), 0);
        float ratio = Math.min(progress / (float) MAX_PROGRESS, 1.0f);
        int filled = (int) (ratio * BAR_LENGTH);
        int empty = BAR_LENGTH - filled;

        // 彩色进度条
        Component bar = Component.literal("|".repeat(Math.max(0, filled))).withStyle(ChatFormatting.GREEN)
                .append(Component.literal("|".repeat(Math.max(0, empty))).withStyle(ChatFormatting.DARK_GRAY));

        String displayName = switch (lastFluid) {
            case "minecraft:water" -> "水";
            case "minecraft:lava" -> "熔岩";
            case "minecraft:powder_snow" -> "细雪";
            case "minecraft:milk" -> "奶";
            default -> lastFluid;
        };

        event.getToolTip().add(Component.literal("曾装有：").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(displayName).withStyle(ChatFormatting.AQUA)));

        event.getToolTip().add(Component.literal("装填进度: ").withStyle(ChatFormatting.GRAY)
                .append(bar));
    }

    @SubscribeEvent
    public static void onFinishUse(LivingEntityUseItemEvent.Finish event) {

        ItemStack original = event.getItem();

        if (!original.is(Items.MILK_BUCKET)) {
            return;
        }

        ItemStack result = event.getResultStack();

        if (!result.is(Items.BUCKET)) {
            return;
        }

        // 保留附魔
        ItemEnchantments ench =
                original.get(DataComponents.ENCHANTMENTS);

        if (ench != null && !ench.isEmpty()) {
            result.set(
                    DataComponents.ENCHANTMENTS,
                    ench
            );
        }

        // 保留自定义名称
        if (original.has(DataComponents.CUSTOM_NAME)) {
            result.set(
                    DataComponents.CUSTOM_NAME,
                    original.get(DataComponents.CUSTOM_NAME)
            );
        }

        // 记录来源
        result.set(
                ModDataComponents.LAST_FLUID.get(),
                "minecraft:milk"
        );

        // 如果你希望喝完后进度清零
        result.set(
                ModDataComponents.PROGRESS.get(),
                0
        );
    }

    @SubscribeEvent
    public static void onMilkCow(PlayerInteractEvent.EntityInteract event) {

        if (!(event.getTarget() instanceof Cow)) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack original = player.getItemInHand(event.getHand());

        if (!original.is(Items.BUCKET)) {
            return;
        }

        ItemEnchantments ench =
                original.get(DataComponents.ENCHANTMENTS);

        boolean hasCustomName =
                original.has(DataComponents.CUSTOM_NAME);

        var customName =
                hasCustomName
                        ? original.get(DataComponents.CUSTOM_NAME)
                        : null;

        Integer progress =
                original.has(ModDataComponents.PROGRESS.get())
                        ? original.get(ModDataComponents.PROGRESS.get())
                        : null;

        player.server.execute(() -> {

            ItemStack current =
                    player.getItemInHand(event.getHand());

            if (!current.is(Items.MILK_BUCKET)) {
                return;
            }

            if (ench != null && !ench.isEmpty()) {
                current.set(
                        DataComponents.ENCHANTMENTS,
                        ench
                );
            }

            if (customName != null) {
                current.set(
                        DataComponents.CUSTOM_NAME,
                        customName
                );
            }

            if (progress != null) {
                current.set(
                        ModDataComponents.PROGRESS.get(),
                        progress
                );
            }

            current.set(
                    ModDataComponents.LAST_FLUID.get(),
                    "minecraft:milk"
            );
        });
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        int xpAmount = event.getAmount();
        if (xpAmount <= 0) return;

        int mainSlot = player.getInventory().getSelectedSlot();
        ItemStack mainHand = player.getInventory().getItem(mainSlot);
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof BucketItem) {
            processBucket(player, mainSlot, mainHand, xpAmount);
        }

        int offSlot = 40;
        ItemStack offHand = player.getInventory().getItem(offSlot);
        if (!offHand.isEmpty() && offHand.getItem() instanceof BucketItem) {
            processBucket(player, offSlot, offHand, xpAmount);
        }
    }

    private static void processBucket(Player player, int slot, ItemStack stack, int xpAmount) {
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof BucketItem bucketItem)) return;
        if (bucketItem.getContent() != Fluids.EMPTY) return;

        String lastFluid = stack.get(ModDataComponents.LAST_FLUID.get());
        if (lastFluid == null) return;

        if (ModEnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) <= 0) return;

        int progress = stack.getOrDefault(ModDataComponents.PROGRESS.get(), 0);
        progress += xpAmount;

        if (progress >= MAX_PROGRESS) {
            fillBucket(player, slot, stack, lastFluid);
        } else {
            stack.set(ModDataComponents.PROGRESS.get(), progress);
        }
    }

    private static void fillBucket(Player player, int slot, ItemStack emptyBucket, String fluidKey) {
        ItemStack filledBucket;

        if ("minecraft:powder_snow".equals(fluidKey)) {
            filledBucket = new ItemStack(Items.POWDER_SNOW_BUCKET);
        } else if ("minecraft:milk".equals(fluidKey)) {
            filledBucket = new ItemStack(Items.MILK_BUCKET);
        } else {
            Identifier fluidId = Identifier.tryParse(fluidKey);
            if (fluidId == null) return;

            Fluid fluid = BuiltInRegistries.FLUID.get(fluidId)
                    .map(Holder::value)
                    .orElse(Fluids.EMPTY);
            if (fluid == Fluids.EMPTY) return;

            var bucketItem = fluid.getBucket();
            filledBucket = new ItemStack(bucketItem);
        }

        // 复制附魔和自定义名称
        if (emptyBucket.has(DataComponents.ENCHANTMENTS)) {
            filledBucket.set(DataComponents.ENCHANTMENTS, emptyBucket.get(DataComponents.ENCHANTMENTS));
        }
        if (emptyBucket.has(DataComponents.CUSTOM_NAME)) {
            filledBucket.set(DataComponents.CUSTOM_NAME, emptyBucket.get(DataComponents.CUSTOM_NAME));
        }
        filledBucket.remove(ModDataComponents.PROGRESS.get());

        player.getInventory().setItem(slot, filledBucket);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BUCKET_FILL,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
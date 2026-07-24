package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.item.custom.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hasoook.MOD_ID);

    // ===== 标枪 (Spears) =====
    public static final DeferredItem<Item> SLIME_SPEAR = ITEMS.registerItem("slime_spear",
            SlimeSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> FIREWORK_ROCKET_SPEAR = ITEMS.registerItem("firework_rocket_spear",
            FireworkRocketSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> HEAVY_SPEAR = ITEMS.registerItem("heavy_spear",
            HeavySpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> AMETHYST_SHARD_SPEAR = ITEMS.registerItem("amethyst_shard_spear",
            AmethystShardSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> END_CRYSTAL_SPEAR = ITEMS.registerItem("end_crystal_spear",
            EndCrystalSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> NETHER_STAR_SPEAR = ITEMS.registerItem("nether_star_spear",
            NetherStarSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> LIGHTNING_SPEAR = ITEMS.registerItem("lightning_spear",
            LightningSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> END_ROD_SPEAR = ITEMS.registerItem("end_rod_spear",
            EndRodSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> PISTON_SPEAR = ITEMS.registerItem("piston_spear",
            PistonSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> STICKY_PISTON_SPEAR = ITEMS.registerItem("sticky_piston_spear",
            PistonSpearItem::new, Item.Properties::new);

    // ===== 功能物品 (Functional Items) =====
    public static final DeferredItem<Item> SEVOWER = ITEMS.registerItem("sevower",
            SevowerItem::new, Item.Properties::new);
    public static final DeferredItem<Item> RECOVERY_CLOCK = ITEMS.registerItem("recovery_clock",
            RecoveryClockItem::new, Item.Properties::new);
    public static final DeferredItem<Item> ECHO_ARROW = ITEMS.registerItem("echo_arrow",
            EchoArrowItem::new, Item.Properties::new);
    public static final DeferredItem<Item> COPPER_ARROW = ITEMS.registerItem("copper_arrow",
            CopperArrowItem::new, Item.Properties::new);
    public static final DeferredItem<Item> ECHO_BOTTLE = ITEMS.registerItem("echo_bottle",
            EchoBottleItem::new, Item.Properties::new);
    public static final DeferredItem<Item> SONIC_BOOM_BOTTLE = ITEMS.registerItem("sonic_boom_bottle",
            SonicBoomBottleItem::new, Item.Properties::new);
    public static final DeferredItem<Item> MAGIC_PAINTBRUSH_AND_PAPER = ITEMS.registerItem("magic_paintbrush_and_paper",
            MagicPaintbrushAndPaperItem::new, Item.Properties::new);
    public static final DeferredItem<Item> HEAVY_HALBERD = ITEMS.registerItem("heavy_halberd",
            HeavyHalberdItem::new, Item.Properties::new);
    public static final DeferredItem<Item> MOB_HEAD = ITEMS.registerItem("mob_head",
            properties -> new MobHeadItem(ModBlocks.MOB_HEAD.get(), properties));
    public static final DeferredItem<Item> ARMOR_STAND_SWORD = ITEMS.registerItem("armor_stand_sword",
            ArmorStandSwordItem::new, Item.Properties::new);
    public static final DeferredItem<Item> SOCK = ITEMS.registerItem("sock",
            ThrowableSockItem::new, Item.Properties::new);
    public static final DeferredItem<Item> COPPER_GOLEM_CONTROLLER = ITEMS.registerItem("copper_golem_controller",
            CopperGolemControllerItem::new, Item.Properties::new);
    public static final DeferredItem<Item> POKER = ITEMS.registerItem("poker",
            PokerItem::new);
    public static final DeferredItem<Item> BOTTLED_LIGHTNING = ITEMS.registerItem("bottled_lightning",
            BottledLightningItem::new, Item.Properties::new);

    // ===== 防具 (Armor) =====
    public static final DeferredItem<Item> COPPER_SHIELD = ITEMS.registerItem("copper_shield",
            properties -> new ShieldItem(properties
                    .durability(256)
                    .component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                    .repairable(ItemTags.WOODEN_TOOL_MATERIALS)
                    .equippableUnswappable(EquipmentSlot.OFFHAND)
                    .component(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(
                            0.25F, 1.0F,
                            List.of(new BlocksAttacks.DamageReduction(
                                    90.0F, Optional.empty(), 0.0F, 1.0F)),
                            new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                            Optional.of(DamageTypeTags.BYPASSES_SHIELD),
                            Optional.of(SoundEvents.SHIELD_BLOCK),
                            Optional.of(SoundEvents.SHIELD_BREAK)))
                    .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)));
    public static final DeferredItem<Item> SOCKS = ITEMS.registerItem("socks",
            SocksItem::new);
    // ===== 方块物品 (Block Items) =====
    public static final DeferredItem<Item> BUILDING_BLOCK = ITEMS.registerItem("building_block",
            properties -> new BuildingBlockItem(ModBlocks.BUILDING_BLOCK.get(), properties),
            Item.Properties::new);

    // ===== 合成材料 (Crafting Materials) =====
    public static final DeferredItem<Item> PISTON_SPEAR_ROD = ITEMS.registerItem("piston_spear_rod",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> PISTON_SPEAR_HEAD = ITEMS.registerItem("piston_spear_head",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> STICKY_PISTON_SPEAR_HEAD = ITEMS.registerItem("sticky_piston_spear_head",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> CARD_PROJECTILE = ITEMS.registerItem("card_projectile",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> CHARGE_BOTTLE = ITEMS.registerItem("charge_bottle",
            Item::new, Item.Properties::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

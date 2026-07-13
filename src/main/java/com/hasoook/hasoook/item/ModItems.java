package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.item.custom.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hasoook.MOD_ID);

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
    public static final DeferredItem<Item> SEVOWER = ITEMS.registerItem("sevower",
            SevowerItem::new, Item.Properties::new);
    public static final DeferredItem<Item> RECOVERY_CLOCK = ITEMS.registerItem("recovery_clock",
            RecoveryClockItem::new, Item.Properties::new);
    public static final DeferredItem<Item> ECHO_ARROW = ITEMS.registerItem("echo_arrow",
            EchoArrowItem::new, Item.Properties::new);
    public static final DeferredItem<Item> ECHO_BOTTLE = ITEMS.registerItem("echo_bottle",
            EchoBottleItem::new, Item.Properties::new);
    public static final DeferredItem<Item> SONIC_BOOM_BOTTLE = ITEMS.registerItem("sonic_boom_bottle",
            SonicBoomBottleItem::new, Item.Properties::new);
    public static final DeferredItem<Item> MAGIC_PAINTBRUSH_AND_PAPER = ITEMS.registerItem("magic_paintbrush_and_paper",
            MagicPaintbrushAndPaperItem::new, Item.Properties::new);
    public static final DeferredItem<Item> HEAVY_HALBERD = ITEMS.registerItem("heavy_halberd",
            HeavyHalberdItem::new, Item.Properties::new);
    public static final DeferredItem<Item> PISTON_SPEAR = ITEMS.registerItem("piston_spear",
            PistonSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> PISTON_SPEAR_ROD = ITEMS.registerItem("piston_spear_rod",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> PISTON_SPEAR_HEAD = ITEMS.registerItem("piston_spear_head",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> STICKY_PISTON_SPEAR = ITEMS.registerItem("sticky_piston_spear",
            PistonSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> STICKY_PISTON_SPEAR_HEAD = ITEMS.registerItem("sticky_piston_spear_head",
            Item::new, Item.Properties::new);
    public static final DeferredItem<Item> MOB_HEAD = ITEMS.registerItem("mob_head",
            properties -> new MobHeadItem(ModBlocks.MOB_HEAD.get(), properties.rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> END_ROD_SPEAR = ITEMS.registerItem("end_rod_spear",
            EndRodSpearItem::new, Item.Properties::new);
    public static final DeferredItem<Item> ARMOR_STAND_SWORD = ITEMS.registerItem("armor_stand_sword",
            ArmorStandSwordItem::new, () -> new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> SOCKS = ITEMS.registerItem("socks",
            properties -> new SocksItem(properties.humanoidArmor(ModArmorMaterials.SOCKS, ArmorType.BOOTS)));
    public static final DeferredItem<Item> SOCK = ITEMS.registerItem("sock",
            ThrowableSockItem::new, Item.Properties::new);
    public static final DeferredItem<Item> COPPER_GOLEM_BATTLE_CHIP = ITEMS.registerItem("copper_golem_battle_chip",
            CopperGolemBattleChipItem::new, Item.Properties::new);
    public static final DeferredItem<Item> CHARGED_COPPER_SWORD = ITEMS.registerItem("charged_copper_sword",
            properties -> new ChargedCopperSwordItem(
                    ToolMaterial.COPPER.applySwordProperties(properties, 3.0F, -2.4F)),
            Item.Properties::new);
    public static final DeferredItem<Item> CHARGED_COPPER_PICKAXE = ITEMS.registerItem("charged_copper_pickaxe",
            properties -> new ChargedCopperPickaxeItem(
                    ToolMaterial.COPPER.applyToolProperties(properties,
                            BlockTags.MINEABLE_WITH_PICKAXE, 1.0F, -2.8F, 0.0F)),
            Item.Properties::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.custom.FireworkRocketSpearItem;
import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.function.Function;

public class ModItems {
    public static final Item SLIME_SPEAR = registerItem("slime_spear",
            SlimeSpearItem::new);
    public static final Item FIREWORK_ROCKET_SPEAR = registerItem("firework_rocket_spear",
            FireworkRocketSpearItem::new);

    private static Item registerItem(String name, Function<Item.Settings, Item> factory) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Hasoook.id(name));
        Item.Settings settings = new Item.Settings().registryKey(key);
        return Registry.register(Registries.ITEM, key, factory.apply(settings));
    }

    public static void initialize() {
        Hasoook.LOGGER.info("Registering Mod Items for " + Hasoook.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(SLIME_SPEAR);
            entries.add(FIREWORK_ROCKET_SPEAR);
        });
    }
}

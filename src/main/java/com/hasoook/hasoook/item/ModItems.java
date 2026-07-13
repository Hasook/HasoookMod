package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.custom.FireworkRocketSpearItem;
import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public class ModItems {
    public static final Item SLIME_SPEAR = registerItem("slime_spear",
            settings -> new SlimeSpearItem(settings));
    public static final Item FIREWORK_ROCKET_SPEAR = registerItem("firework_rocket_spear",
            settings -> new FireworkRocketSpearItem(settings));

    private static Item registerItem(String name, ItemFactory factory) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Hasoook.id(name));
        Item.Settings settings = new Item.Settings().registryKey(key);
        return Registry.register(Registries.ITEM, key, factory.create(settings));
    }

    @FunctionalInterface
    private interface ItemFactory {
        Item create(Item.Settings settings);
    }

    public static void initialize() {
        // Called to trigger class loading and static registration
    }
}

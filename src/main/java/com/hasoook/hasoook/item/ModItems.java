package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.custom.FireworkRocketSpearItem;
import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import com.hasoook.hasoook.item.custom.SocksItem;
import com.hasoook.hasoook.item.custom.ThrowableSockItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.equipment.EquipmentType;
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

    // 一双袜子（防具，穿在脚上）
    public static final Item SOCKS = registerItem("socks",
            settings -> new SocksItem(settings
                    .armor(ModArmorMaterials.SOCKS, EquipmentType.BOOTS)
                    .maxCount(1)));
    // 单只袜子（可投掷/近战糊脸）
    public static final Item SOCK = registerItem("sock",
            ThrowableSockItem::new);

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
            entries.add(SOCKS);
            entries.add(SOCK);
        });
    }
}

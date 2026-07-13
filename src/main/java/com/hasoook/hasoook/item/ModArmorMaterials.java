package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Util;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;

import java.util.EnumMap;

public class ModArmorMaterials {
    static ResourceKey<? extends Registry<EquipmentAsset>> ROOT_ID =
            ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset"));
    public static ResourceKey<EquipmentAsset> SOCKS_ASSET =
            ResourceKey.create(ROOT_ID, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "socks"));

    public static final ArmorMaterial SOCKS = new ArmorMaterial(
            65,
            Util.make(new EnumMap<>(ArmorType.class), map -> {
                map.put(ArmorType.BOOTS, 1);
                map.put(ArmorType.LEGGINGS, 0);
                map.put(ArmorType.CHESTPLATE, 0);
                map.put(ArmorType.HELMET, 0);
                map.put(ArmorType.BODY, 0);
            }),
            15,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0f,
            0f,
            ItemTags.WOOL,
            SOCKS_ASSET
    );
}

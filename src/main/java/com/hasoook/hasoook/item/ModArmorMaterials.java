package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.EnumMap;

public class ModArmorMaterials {
    static final RegistryKey<? extends Registry<EquipmentAsset>> ROOT_ID =
            RegistryKey.ofRegistry(Identifier.ofVanilla("equipment_asset"));
    public static final RegistryKey<EquipmentAsset> SOCKS_ASSET =
            RegistryKey.of(ROOT_ID, Hasoook.id("socks"));

    public static final ArmorMaterial SOCKS = new ArmorMaterial(
            65,
            Util.make(new EnumMap<>(EquipmentType.class), map -> {
                map.put(EquipmentType.BOOTS, 1);
                map.put(EquipmentType.LEGGINGS, 0);
                map.put(EquipmentType.CHESTPLATE, 0);
                map.put(EquipmentType.HELMET, 0);
            }),
            15,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            0f,
            0f,
            ItemTags.WOOL,
            SOCKS_ASSET
    );
}

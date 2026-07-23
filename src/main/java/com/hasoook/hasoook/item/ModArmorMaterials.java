package com.hasoook.hasoook.item;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;

import java.util.EnumMap;

public class ModArmorMaterials {
    static ResourceKey<? extends Registry<EquipmentAsset>> ROOT_ID =
            ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset"));
    public static ResourceKey<EquipmentAsset> SOCKS_ASSET =
            ResourceKey.create(ROOT_ID, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "socks"));
    public static ResourceKey<EquipmentAsset> CHARGED_COPPER_ASSET =
            ResourceKey.create(ROOT_ID, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "charged_copper"));

    public static final TagKey<Item> CHARGED_COPPER_REPAIR =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "charged_copper_repair"));

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

    public static final ArmorMaterial CHARGED_COPPER = new ArmorMaterial(
            200,
            Util.make(new EnumMap<>(ArmorType.class), map -> {
                map.put(ArmorType.BOOTS, 2);
                map.put(ArmorType.LEGGINGS, 4);
                map.put(ArmorType.CHESTPLATE, 5);
                map.put(ArmorType.HELMET, 2);
                map.put(ArmorType.BODY, 0);
            }),
            18,
            SoundEvents.ARMOR_EQUIP_IRON,
            0f,
            0f,
            CHARGED_COPPER_REPAIR,
            CHARGED_COPPER_ASSET
    );
}

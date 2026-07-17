package com.hasoook.hasoook.enchantment;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.enchantment.custom.DebtshotEffect;
import com.hasoook.hasoook.enchantment.custom.GivingEnchantmentEffect;
import com.hasoook.hasoook.enchantment.custom.HollowEnchantmentEffect;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.SetValue;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

import java.util.List;

public class ModEnchantments {
    // 平民的决心
    public static final ResourceKey<Enchantment> COMMONERS_RESOLVE = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "commoners_resolve"));
    // 限流
    public static final ResourceKey<Enchantment> UNDERTOW = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "undertow"));
    // 空心
    public static final ResourceKey<Enchantment> HOLLOW = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "hollow"));
    // 限流
    public static final ResourceKey<Enchantment> DEBTSHOT = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "debtshot"));
    // 路易
    public static final ResourceKey<Enchantment> LOUIS = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "louis"));
    // 给予
    public static final ResourceKey<Enchantment> GIVING = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "giving"));
    // 出千
    public static final ResourceKey<Enchantment> CHEATING = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "cheating"));
    // 飞牌
    public static final ResourceKey<Enchantment> CARD_THROW = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "card_throw"));

    public static void bootstrap(BootstrapContext<Enchantment> context) {
        var enchantments = context.lookup(Registries.ENCHANTMENT);
        var items = context.lookup(Registries.ITEM);

        register(context, COMMONERS_RESOLVE, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.WEAPON_ENCHANTABLE),
                        5,
                        1,
                        Enchantment.dynamicCost(5, 7),
                        Enchantment.dynamicCost(25, 7),
                        2,
                        EquipmentSlotGroup.MAINHAND))
                .exclusiveWith(enchantments.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
        );
        register(context, UNDERTOW, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.TRIDENT_ENCHANTABLE),   // 只允许三叉戟
                        2,                                                // 权重
                        3,                                                // 最大等级
                        Enchantment.dynamicCost(17, 7),                   // 最小附魔台等级成本
                        Enchantment.dynamicCost(50, 7),                   // 最大附魔台等级成本
                        4,                                                // 铁砧合并花费
                        EquipmentSlotGroup.HAND))                     // 主手生效
                .exclusiveWith(HolderSet.direct(enchantments.getOrThrow(Enchantments.RIPTIDE))) // 与激流互斥
                .withSpecialEffect(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH,
                        new AddValue(LevelBasedValue.perLevel(1.5F, 0.75F)))
                .withSpecialEffect(EnchantmentEffectComponents.TRIDENT_SOUND,
                        List.of(SoundEvents.TRIDENT_RIPTIDE_1, SoundEvents.TRIDENT_RIPTIDE_2, SoundEvents.TRIDENT_RIPTIDE_3))
        );
        register(context, HOLLOW, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.MACE_ENCHANTABLE),
                        5, 5,
                        Enchantment.dynamicCost(5, 8),
                        Enchantment.dynamicCost(25, 8),
                        2,
                        EquipmentSlotGroup.HAND))
                .exclusiveWith(enchantments.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
                .withEffect(EnchantmentEffectComponents.TICK, new HollowEnchantmentEffect())
        );
        register(context, DEBTSHOT, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.CROSSBOW_ENCHANTABLE),
                        2, 1,
                        Enchantment.constantCost(20),
                        Enchantment.constantCost(50),
                        4,
                        EquipmentSlotGroup.HAND))
                .withEffect(
                        EnchantmentEffectComponents.PROJECTILE_SPAWNED,          // 弹射物生成时触发
                        new DebtshotEffect(LevelBasedValue.constant(0.5F))       // 50% 概率删除
                )
        );
        register(context, LOUIS, Enchantment.enchantment(Enchantment.definition(
                        HolderSet.direct(items.getOrThrow(ResourceKey.create(Registries.ITEM,
                                Identifier.fromNamespaceAndPath("minecraft", "shears")))),
                        4,                                                       // 权重
                        16,                                                      // 最大等级
                        Enchantment.dynamicCost(5, 8),                           // 最小附魔台等级成本
                        Enchantment.dynamicCost(25, 8),                          // 最大附魔台等级成本
                        2,                                                       // 铁砧合并花费
                        EquipmentSlotGroup.HAND))                                // 主手生效
        );
        register(context, GIVING, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.DURABILITY_ENCHANTABLE),
                        1,                                                       // 权重
                        1,                                                       // 最大等级
                        Enchantment.constantCost(25),                            // 最小附魔台等级成本
                        Enchantment.constantCost(50),                            // 最大附魔台等级成本
                        8,                                                       // 铁砧合并花费
                        EquipmentSlotGroup.MAINHAND))                            // 主手生效
                .withEffect(EnchantmentEffectComponents.POST_ATTACK, EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, new GivingEnchantmentEffect())
        );
        register(context, CHEATING, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.DURABILITY_ENCHANTABLE),
                        4, 1,
                        Enchantment.constantCost(30),
                        Enchantment.constantCost(60),
                        4,
                        EquipmentSlotGroup.MAINHAND))
        );
        register(context, CARD_THROW, Enchantment.enchantment(Enchantment.definition(
                        items.getOrThrow(ItemTags.DURABILITY_ENCHANTABLE),
                        2, 1,
                        Enchantment.constantCost(20),
                        Enchantment.constantCost(50),
                        2,
                        EquipmentSlotGroup.MAINHAND))
        );

    }

    private static void register(BootstrapContext<Enchantment> registry, ResourceKey<Enchantment> key,
                                 Enchantment.Builder builder) {
        registry.register(key, builder.build(key.identifier()));
    }
}

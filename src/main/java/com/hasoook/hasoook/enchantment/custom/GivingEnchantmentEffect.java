package com.hasoook.hasoook.enchantment.custom;

import com.hasoook.hasoook.Hasoook;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public record GivingEnchantmentEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<GivingEnchantmentEffect> CODEC =
            MapCodec.unit(GivingEnchantmentEffect::new);

    private static final ResourceKey<Enchantment> GIVING_KEY = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "giving"));

    @Override
    public void apply(@NonNull ServerLevel serverLevel, int enchantmentLevel, @NonNull EnchantedItemInUse enchantedItemInUse, @NonNull Entity entity, @NonNull Vec3 vec3) {
        // 攻击者 = enchantedItemInUse.owner(), 目标 = entity
        if (enchantedItemInUse.owner() instanceof LivingEntity attacker && entity instanceof LivingEntity target) {
            ItemStack cursedStack = attacker.getMainHandItem();
            if (!cursedStack.isEmpty()) {
                // 1. 移除「给予」附魔
                removeGivingEnchantment(cursedStack);

                // 2. 从攻击者手中移除物品
                attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

                // 3. 将物品给予目标：装备 → 对应装备栏，否则 → 主手
                EquipmentSlot targetSlot = target.getEquipmentSlotForItem(cursedStack);
                ItemStack existingStack = target.getItemBySlot(targetSlot);
                if (!existingStack.isEmpty()) {
                    target.drop(existingStack, true, false);
                }
                target.setItemSlot(targetSlot, cursedStack);
            }
        }
    }

    private void removeGivingEnchantment(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        mutable.removeIf(holder -> holder.is(GIVING_KEY));
        ItemEnchantments newEnchantments = mutable.toImmutable();
        if (newEnchantments.isEmpty()) {
            stack.remove(DataComponents.ENCHANTMENTS);
        } else {
            stack.set(DataComponents.ENCHANTMENTS, newEnchantments);
        }
    }

    @Override
    public @NonNull MapCodec<? extends EnchantmentEntityEffect> codec() {
        return CODEC;
    }
}

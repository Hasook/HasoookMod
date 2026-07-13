package com.hasoook.hasoook.item.custom;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class HeavySpearItem extends Item {
    public HeavySpearItem(Properties properties) {
        super(properties.spear(ToolMaterial.NETHERITE, 1.4F, 1.2F, 1.0F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F));
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, @NonNull LivingEntity attacker) {

    }

    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity livingEntity, @NonNull ItemStack stack, int remainingUseDuration) {
        int useTick = getUseDuration(stack, livingEntity) - livingEntity.getUseItemRemainingTicks();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        if (useTick >= maxActive || useTick <= kinetic) {
            return;
        }
        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (level instanceof ServerLevel && entity instanceof LivingEntity livingEntity) {
            int useTick = getUseDuration(stack, livingEntity) - livingEntity.getUseItemRemainingTicks();
            int maxActive = getMaxActiveTicks(stack);
            int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

            if (useTick >= maxActive || useTick <= kinetic) {
            }
        }
        super.inventoryTick(stack, level, entity, slot);
    }

    // 获取蓄力时间
    @Nullable
    private static KineticWeapon getKinetic(ItemStack stack) {
        return stack.get(DataComponents.KINETIC_WEAPON);
    }

    // 获取最大使用时间
    private static int getMaxActiveTicks(ItemStack stack) {
        KineticWeapon kinetic = getKinetic(stack);
        if (kinetic == null) return Integer.MAX_VALUE;

        return kinetic.computeDamageUseDuration();
    }

    @Override
    public int getUseDuration(@NonNull ItemStack itemStack, @NonNull LivingEntity livingEntity) {
        return 72000; // 设置长按的时间
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack itemStack) {
        return ItemUseAnimation.SPEAR; // 使用矛的动画
    }
}
package com.hasoook.hasoook.enchantment.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public record HollowEnchantmentEffect() implements EnchantmentEntityEffect {
    public static final MapCodec<HollowEnchantmentEffect> CODEC =
            MapCodec.unit(HollowEnchantmentEffect::new);

    @Override
    public void apply(@NonNull ServerLevel serverLevel, int enchantmentLevel, @NonNull EnchantedItemInUse enchantedItemInUse, @NonNull Entity entity, @NonNull Vec3 vec3) {
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0));
        }
    }

    @Override
    public @NonNull MapCodec<? extends EnchantmentEntityEffect> codec() {
        return CODEC;
    }
}
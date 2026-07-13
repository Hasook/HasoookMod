package com.hasoook.hasoook.enchantment;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.enchantment.custom.DebtshotEffect;
import com.hasoook.hasoook.enchantment.custom.GivingEnchantmentEffect;
import com.hasoook.hasoook.enchantment.custom.HollowEnchantmentEffect;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEnchantmentEffects {
    public static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ENTITY_ENCHANTMENT_EFFECTS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, Hasoook.MOD_ID);

    public static final Supplier<MapCodec<? extends EnchantmentEntityEffect>> HOLLOW =
            ENTITY_ENCHANTMENT_EFFECTS.register("hollow", () -> HollowEnchantmentEffect.CODEC);
    public static final Supplier<MapCodec<? extends EnchantmentEntityEffect>> DEBTSHOT =
            ENTITY_ENCHANTMENT_EFFECTS.register("debtshot", () -> DebtshotEffect.CODEC);
    public static final Supplier<MapCodec<? extends EnchantmentEntityEffect>> GIVING =
            ENTITY_ENCHANTMENT_EFFECTS.register("giving", () -> GivingEnchantmentEffect.CODEC);

    public static void register(IEventBus eventBus) {
        ENTITY_ENCHANTMENT_EFFECTS.register(eventBus);
    }
}
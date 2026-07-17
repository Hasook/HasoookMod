package com.hasoook.hasoook.effect;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, Hasoook.MOD_ID);

    public static final Holder<MobEffect> TEMPORAL_DISTORTION_EFFECT = MOB_EFFECTS.register("temporal_distortion",
            () -> new TemporalDistortionEffect(MobEffectCategory.NEUTRAL, 0x29dfeb));

    public static final Holder<MobEffect> DISABILITY = MOB_EFFECTS.register("disability",
            () -> new DisabilityEffect(MobEffectCategory.HARMFUL, 0x8B0000));

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
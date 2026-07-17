package com.hasoook.hasoook.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public class ModDamageSources {
    public static DamageSource temporalCollapse(ServerLevel level) {
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.TEMPORAL_COLLAPSE);
        return new DamageSource(type);
    }

    /// 积木踩踏伤害
    public static DamageSource buildingBlock(ServerLevel level) {
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.BUILDING_BLOCK);
        return new DamageSource(type);
    }

}
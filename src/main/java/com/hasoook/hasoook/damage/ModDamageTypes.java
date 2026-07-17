package com.hasoook.hasoook.damage;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.core.registries.Registries;

public class ModDamageTypes {
    public static final ResourceKey<DamageType> TEMPORAL_COLLAPSE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "temporal_collapse")
            );

    /// 积木踩踏伤害类型
    public static final ResourceKey<DamageType> BUILDING_BLOCK =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "building_block")
            );

}
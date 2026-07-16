package com.hasoook.hasoook.entity;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.ThrownSockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public class ModEntities {
    public static final RegistryKey<EntityType<?>> THROWN_SOCK_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, Hasoook.id("thrown_sock"));

    public static final EntityType<ThrownSockEntity> THROWN_SOCK = Registry.register(
            Registries.ENTITY_TYPE,
            THROWN_SOCK_KEY,
            EntityType.Builder.<ThrownSockEntity>create(ThrownSockEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .maxTrackingRange(4)
                    .trackingTickInterval(10)
                    .build(THROWN_SOCK_KEY)
    );

    public static void initialize() {
        // Trigger class loading
    }
}

package com.hasoook.hasoook.entity;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.AmethystShardProjectile;
import com.hasoook.hasoook.entity.custom.ArmorStandSwordProjectile;
import com.hasoook.hasoook.entity.custom.EchoArrowProjectile;
import com.hasoook.hasoook.entity.custom.HeavyHalberdProjectile;
import com.hasoook.hasoook.entity.custom.SevowerProjectile;
import com.hasoook.hasoook.entity.custom.ThrownSockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Hasoook.MOD_ID);

    public static final ResourceKey<EntityType<?>> AMETHYST_SHARD_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "amethyst_shard"));
    public static final ResourceKey<EntityType<?>> SEVOWER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "sevower"));
    public static final ResourceKey<EntityType<?>> HEAVY_HALBERD_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "heavy_halberd"));
    public static final ResourceKey<EntityType<?>> ECHO_ARROW_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "echo_arrow"));
    public static final ResourceKey<EntityType<?>> ARMOR_STAND_SWORD_PROJECTILE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "armor_stand_sword_projectile"));
    public static final ResourceKey<EntityType<?>> THROWN_SOCK_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "thrown_sock"));

    public static final Supplier<EntityType<AmethystShardProjectile>> AMETHYST_SHARD =
            ENTITY_TYPES.register("amethyst_shard", () -> EntityType.Builder.<AmethystShardProjectile>of(AmethystShardProjectile::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).build(AMETHYST_SHARD_KEY));
    public static final Supplier<EntityType<SevowerProjectile>> SEVOWER =
            ENTITY_TYPES.register("sevower", () -> EntityType.Builder.<SevowerProjectile>of(SevowerProjectile::new, MobCategory.MISC)
                    .sized(0.8f, 0.4f).build(SEVOWER_KEY));
    public static final Supplier<EntityType<EchoArrowProjectile>> ECHO_ARROW =
            ENTITY_TYPES.register("echo_arrow", () -> EntityType.Builder.<EchoArrowProjectile>of(EchoArrowProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20).build(ECHO_ARROW_KEY));
    public static final Supplier<EntityType<HeavyHalberdProjectile>> HEAVY_HALBERD =
            ENTITY_TYPES.register("heavy_halberd", () -> EntityType.Builder.<HeavyHalberdProjectile>of(HeavyHalberdProjectile::new, MobCategory.MISC)
                    .sized(0.8f, 0.4f).build(HEAVY_HALBERD_KEY));
    public static final Supplier<EntityType<ArmorStandSwordProjectile>> ARMOR_STAND_SWORD_PROJECTILE =
            ENTITY_TYPES.register("armor_stand_sword_projectile", () -> EntityType.Builder.<ArmorStandSwordProjectile>of(ArmorStandSwordProjectile::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).build(ARMOR_STAND_SWORD_PROJECTILE_KEY));
    public static final Supplier<EntityType<ThrownSockEntity>> THROWN_SOCK =
            ENTITY_TYPES.register("thrown_sock", () -> EntityType.Builder.<ThrownSockEntity>of(ThrownSockEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build(THROWN_SOCK_KEY));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}

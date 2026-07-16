package com.hasoook.hasoook.event;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.component.SockFaceData;
import com.hasoook.hasoook.item.custom.SocksItem;
import com.hasoook.hasoook.network.ModNetworkInit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SocksEventHandler {

    private static final Map<UUID, Integer> ACCUMULATOR = new HashMap<>();
    private static final Map<UUID, Integer> PARTICLE_TIMER = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();
    private static final Map<UUID, Integer> BLOCK_DESTROY_TIMER = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FLEE_TICK = new HashMap<>();

    private static final int WEAR_WALK = 1;
    private static final int WEAR_SPRINT = 2;
    private static final int WEAR_JUMP = 10;

    private static final int[] POISON_COLORS = {
            0x000000, 0xFF7BA05B, 0xFF5A8A3C, 0xFF3D6B22, 0xFF254D0F, 0xFF123004, 0xFF061801,
    };
    private static final int[] PARTICLE_INTERVAL = {999, 20, 12, 8, 5, 3, 2};
    private static final int[] PARTICLE_COUNT = {0, 1, 2, 4, 8, 14, 22};

    private static final double[] FLEE_RANGE = {0, 0, 0, 5, 8, 12, 16};
    private static final int FLEE_PATH_INTERVAL = 15;
    private static final double FLEE_SPEED = 1.2;
    private static final int FLEE_MAP_CLEAN_INTERVAL = 200;
    private static int fleeCleanTimer = 0;

    private static final int POISON_DURATION = 200;
    private static final double[] POISON_RANGE = {0, 0, 0, 0, 3, 5, 8};
    private static final int[] POISON_AMPLIFIER = {0, 0, 0, 0, 0, 1, 2};

    private static final double[] DESTROY_RANGE = {0, 0, 0, 0, 0, 6, 10};
    private static final int BLOCK_DESTROY_INTERVAL = 6;
    private static final int BLOCK_DESTROY_ATTEMPTS = 10;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                onPlayerTick(player);
            }
        });
    }

    private static void onPlayerTick(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        UUID uuid = player.getUuid();

        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);
        if (!(feetStack.getItem() instanceof SocksItem)) {
            ACCUMULATOR.remove(uuid);
            PARTICLE_TIMER.remove(uuid);
            WAS_ON_GROUND.remove(uuid);
            BLOCK_DESTROY_TIMER.remove(uuid);
            LAST_FLEE_TICK.remove(uuid);
            onSockFaceTick(player);
            return;
        }

        int baseWear = feetStack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
        int pending = ACCUMULATOR.getOrDefault(uuid, 0);
        int currentWear = baseWear + pending;
        int currentStage = SocksItem.getStage(currentWear);

        if (currentStage > 0) {
            int timer = PARTICLE_TIMER.getOrDefault(uuid, 0) + 1;
            int interval = PARTICLE_INTERVAL[currentStage];
            if (timer >= interval) {
                int count = PARTICLE_COUNT[currentStage];
                world.spawnParticles(
                        TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, POISON_COLORS[currentStage]),
                        player.getX(), player.getY() + 0.25, player.getZ(),
                        count, 0.35, 0.25, 0.35, 0.02);
                timer = 0;
            }
            PARTICLE_TIMER.put(uuid, timer);
        }

        if (currentStage >= 3) {
            applyFleeAura(world, player, currentStage);
        }
        if (currentStage >= 4) {
            applyPoisonAura(world, player, currentStage);
        }
        if (currentStage >= 5) {
            applyBlockDestructionAura(world, player, uuid, currentStage);
        }
        if (currentStage >= 5) {
            int selfPoisonAmp = POISON_AMPLIFIER[currentStage] - 1;
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.POISON);
            if (existing == null || existing.getAmplifier() < selfPoisonAmp
                    || (existing.getAmplifier() == selfPoisonAmp && existing.getDuration() <= 60)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, selfPoisonAmp,
                        false, true));
            }
            if (currentStage >= 6) {
                StatusEffectInstance nausea = player.getStatusEffect(StatusEffects.NAUSEA);
                if (nausea == null || nausea.getDuration() <= 60) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0,
                            false, false));
                }
            }
        }

        boolean onGround = player.isOnGround();
        boolean wasOnGrnd = WAS_ON_GROUND.getOrDefault(uuid, true);
        int addWear = 0;

        if (wasOnGrnd && !onGround && player.getVelocity().y > 0) {
            addWear += WEAR_JUMP;
        }
        if (onGround && player.getVelocity().horizontalLengthSquared() > 0.0001) {
            addWear += player.isSprinting() ? WEAR_SPRINT : WEAR_WALK;
        }
        WAS_ON_GROUND.put(uuid, onGround);

        if (addWear == 0) {
            onSockFaceTick(player);
            return;
        }

        int oldStage = SocksItem.getStage(baseWear + pending);
        pending += addWear;
        int newWear = baseWear + pending;
        int newStage = SocksItem.getStage(newWear);

        if (oldStage != newStage) {
            feetStack.set(ModDataComponents.SOCKS_WEAR, newWear);
            ACCUMULATOR.put(uuid, 0);
        } else {
            ACCUMULATOR.put(uuid, pending);
        }

        onSockFaceTick(player);
    }

    private static void applyFleeAura(ServerWorld world, PlayerEntity player, int stage) {
        double range = FLEE_RANGE[stage];
        Box box = player.getBoundingBox().expand(range);
        int now = player.age;

        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, e -> true)) {
            UUID mobId = mob.getUuid();
            mob.setTarget(null);

            int lastFlee = LAST_FLEE_TICK.getOrDefault(mobId, -FLEE_PATH_INTERVAL);
            if (now - lastFlee < FLEE_PATH_INTERVAL) continue;
            LAST_FLEE_TICK.put(mobId, now);

            Vec3d mobPos = mob.getEntityPos();
            Vec3d playerPos = player.getEntityPos();
            double dist = mobPos.distanceTo(playerPos);

            Vec3d awayDir;
            if (dist < 0.01) {
                awayDir = new Vec3d(world.random.nextDouble() - 0.5, 0, world.random.nextDouble() - 0.5).normalize();
            } else {
                awayDir = mobPos.subtract(playerPos).normalize();
            }

            double fleeDist = Math.max(range - dist + 3, 3);
            Vec3d fleeTarget = mobPos.add(awayDir.x * fleeDist, 0, awayDir.z * fleeDist);

            BlockPos groundPos = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING,
                    BlockPos.ofFloored(fleeTarget)
            );
            fleeTarget = new Vec3d(fleeTarget.x, groundPos.getY(), fleeTarget.z);

            mob.getNavigation().startMovingTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
        }

        fleeCleanTimer++;
        if (fleeCleanTimer >= FLEE_MAP_CLEAN_INTERVAL) {
            fleeCleanTimer = 0;
            int threshold = now - FLEE_MAP_CLEAN_INTERVAL * 2;
            LAST_FLEE_TICK.values().removeIf(tick -> tick < threshold);
        }
    }

    private static void applyPoisonAura(ServerWorld world, PlayerEntity player, int stage) {
        double range = POISON_RANGE[stage];
        int amplifier = POISON_AMPLIFIER[stage];
        Box box = player.getBoundingBox().expand(range);

        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, box, e -> true)) {
            if (entity == player) continue;

            StatusEffectInstance existing = entity.getStatusEffect(StatusEffects.POISON);
            if (existing != null && existing.getAmplifier() > amplifier) continue;
            if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 60) continue;

            entity.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.POISON,
                    POISON_DURATION,
                    amplifier,
                    false,
                    true
            ));
        }
    }

    private static void applyBlockDestructionAura(ServerWorld world, PlayerEntity player, UUID uuid, int stage) {
        int timer = BLOCK_DESTROY_TIMER.getOrDefault(uuid, 0) + 1;
        if (timer < BLOCK_DESTROY_INTERVAL) {
            BLOCK_DESTROY_TIMER.put(uuid, timer);
            return;
        }
        BLOCK_DESTROY_TIMER.put(uuid, 0);

        double range = DESTROY_RANGE[stage];

        for (int attempt = 0; attempt < BLOCK_DESTROY_ATTEMPTS; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double dist = world.random.nextDouble() * range;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            double dy = (world.random.nextDouble() - 0.5) * range * 2;

            BlockPos pos = BlockPos.ofFloored(
                    player.getX() + dx,
                    player.getY() + dy,
                    player.getZ() + dz
            );

            double actualDist = player.getEntityPos().distanceTo(pos.toCenterPos());
            if (actualDist > range) continue;

            double chance = 1.0 - (actualDist / range);
            if (world.random.nextDouble() > chance) continue;

            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.MYCELIUM) || state.isOf(Blocks.PODZOL)) {
                world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
                spawnDestructionParticles(world, pos);
                continue;
            }

            if (isSapling(state)) {
                world.setBlockState(pos, Blocks.DEAD_BUSH.getDefaultState(), 3);
                spawnDestructionParticles(world, pos);
                continue;
            }

            if (isDestroyablePlant(state)) {
                world.breakBlock(pos, false);
                spawnDestructionParticles(world, pos);
            }
        }
    }

    private static boolean isSapling(BlockState state) {
        return state.isOf(Blocks.OAK_SAPLING)
                || state.isOf(Blocks.SPRUCE_SAPLING)
                || state.isOf(Blocks.BIRCH_SAPLING)
                || state.isOf(Blocks.JUNGLE_SAPLING)
                || state.isOf(Blocks.ACACIA_SAPLING)
                || state.isOf(Blocks.DARK_OAK_SAPLING)
                || state.isOf(Blocks.CHERRY_SAPLING)
                || state.isOf(Blocks.MANGROVE_PROPAGULE)
                || state.isOf(Blocks.AZALEA)
                || state.isOf(Blocks.FLOWERING_AZALEA);
    }

    private static boolean isDestroyablePlant(BlockState state) {
        return state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.LEAVES)
                || state.isIn(BlockTags.CROPS)
                || state.isOf(Blocks.SHORT_GRASS)
                || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.LARGE_FERN)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.BROWN_MUSHROOM)
                || state.isOf(Blocks.RED_MUSHROOM)
                || state.isOf(Blocks.CRIMSON_FUNGUS)
                || state.isOf(Blocks.WARPED_FUNGUS)
                || state.isOf(Blocks.CRIMSON_ROOTS)
                || state.isOf(Blocks.WARPED_ROOTS)
                || state.isOf(Blocks.NETHER_SPROUTS)
                || state.isOf(Blocks.HANGING_ROOTS)
                || state.isOf(Blocks.SMALL_DRIPLEAF)
                || state.isOf(Blocks.BIG_DRIPLEAF)
                || state.isOf(Blocks.SPORE_BLOSSOM)
                || state.isOf(Blocks.PINK_PETALS)
                || state.isOf(Blocks.WILDFLOWERS);
    }

    private static void spawnDestructionParticles(ServerWorld world, BlockPos pos) {
        world.spawnParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                3, 0.2, 0.1, 0.2, 0.01
        );
    }

    private static void onSockFaceTick(ServerPlayerEntity player) {
        String data = SockFaceData.getSockFace(player);
        if (data.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        int maxStage = 0;
        boolean changed = false;
        for (String part : data.split(",")) {
            int packed = Integer.parseInt(part);
            int remaining = (packed & 0xFFF) - 1;
            int wearStage = (packed >> 16) & 0xFF;
            if (wearStage > maxStage) maxStage = wearStage;
            if (remaining > 0) {
                int upper = packed & ~0xFFF;
                if (!sb.isEmpty()) sb.append(',');
                sb.append(upper | remaining);
            } else {
                changed = true;
            }
        }
        if (changed) {
            SockFaceData.setSockFace(player, sb.toString());
            ModNetworkInit.syncSockFaceToPlayer(player);
        }

        if (maxStage >= 4) {
            int amplifier = maxStage - 4;
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.POISON);
            boolean shouldSkip = (existing != null && existing.getAmplifier() > amplifier)
                    || (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 20);
            if (!shouldSkip) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, amplifier,
                        false, true));
            }
        } else {
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.POISON);
            if (existing != null && existing.getDuration() > 40) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 30,
                        existing.getAmplifier(), false, true));
            }
        }

        if (maxStage >= 5) {
            int nauseaAmp = maxStage - 5;
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.NAUSEA);
            if (existing == null || existing.getDuration() <= 60) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, nauseaAmp,
                        false, false));
            }
        } else {
            StatusEffectInstance existing = player.getStatusEffect(StatusEffects.NAUSEA);
            if (existing != null && existing.getDuration() > 40) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 30,
                        existing.getAmplifier(), false, false));
            }
        }
    }
}

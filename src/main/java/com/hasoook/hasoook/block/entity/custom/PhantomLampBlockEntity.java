package com.hasoook.hasoook.block.entity.custom;

import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.block.entity.ModBlockEntities;
import com.hasoook.hasoook.item.custom.PhantomLampBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PhantomLampBlockEntity extends BlockEntity {
    private static final int CHARGE_TIME = 100; // 5 seconds at 20 tps
    private static final int FREEZE_TIME = 200; // 10 seconds
    private static final int EFFECT_RADIUS = 5;
    private static final double CHARGE_DISTANCE_SQ = 9.0; // 3 blocks squared
    private static final int PHANTOM_GRAB_DURATION = 140; // 7 seconds total at 20 tps
    private static final double APPROACH_SPEED = 0.28; // blocks per tick toward target
    private static final double ASCEND_SPEED_MIN = 0.14; // starting ascent speed
    private static final double ASCEND_SPEED_MAX = 0.30; // ending ascent speed
    private static final double GRAB_DISTANCE = 1.5; // grab when this close to target
    private static final double SPIRAL_RADIUS_MAX = 3.0; // max horizontal spiral radius
    private static final double SPIRAL_ANGULAR_SPEED = 0.17; // radians per tick (balanced rotation)

    private String lampState = PhantomLampBlockItem.STATE_PRISTINE;
    private int chargeTicks;
    private int freezeTicks;
    private int phantomGrabTicks;
    @Nullable
    private UUID chargingPlayer;
    private final List<GrabTask> grabTasks = new ArrayList<>();

    /**
     * Tracks one phantom's mission: approach target → grab → ascend → drop.
     */
    private static class GrabTask {
        final UUID phantomUuid;
        final UUID targetUuid;
        boolean grabbed;
        boolean discarded; // true when target died and phantom was removed

        GrabTask(UUID phantomUuid, UUID targetUuid) {
            this.phantomUuid = phantomUuid;
            this.targetUuid = targetUuid;
        }
    }

    public PhantomLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHANTOM_LAMP.get(), pos, state);
    }

    /**
     * Called when the lamp block is actually destroyed (not on chunk unload).
     * Releases all prey and discards all phantoms.
     */
    public void onBlockDestroyed() {
        if (level instanceof ServerLevel serverLevel) {
            for (GrabTask task : grabTasks) {
                Entity prey = serverLevel.getEntity(task.targetUuid);
                if (prey != null) {
                    prey.setNoGravity(false);
                    if (prey instanceof Mob mob) {
                        mob.setNoAi(false);
                    }
                }
                Entity phantomEntity = serverLevel.getEntity(task.phantomUuid);
                if (phantomEntity != null && phantomEntity.isAlive()) {
                    phantomEntity.discard();
                }
            }
        }
        grabTasks.clear();
        phantomGrabTicks = 0;
    }

    public String getLampState() {
        return lampState;
    }

    public void setLampState(String state) {
        this.lampState = state;
        setChanged();
    }

    public boolean isPristine() {
        return PhantomLampBlockItem.STATE_PRISTINE.equals(lampState);
    }

    public boolean isBroken() {
        return PhantomLampBlockItem.STATE_BROKEN.equals(lampState);
    }

    public boolean canFreeze() {
        return isPristine();
    }

    public boolean canSleep() {
        return isPristine() || PhantomLampBlockItem.STATE_REPAIRED.equals(lampState);
    }

    public boolean isFreezing() {
        return freezeTicks > 0;
    }

    public boolean isCharging() {
        return chargeTicks > 0;
    }

    public void startCharging(UUID playerId) {
        if (canFreeze() && chargeTicks == 0 && freezeTicks == 0) {
            this.chargingPlayer = playerId;
            this.chargeTicks = 1;
            setChangedAndSync();
        }
    }

    public void startPhantomGrab(Map<UUID, UUID> phantomToTarget) {
        this.phantomGrabTicks = PHANTOM_GRAB_DURATION;
        this.grabTasks.clear();
        for (var entry : phantomToTarget.entrySet()) {
            this.grabTasks.add(new GrabTask(entry.getKey(), entry.getValue()));
        }
        setChangedAndSync();
    }

    public boolean isPhantomGrabbing() {
        return phantomGrabTicks > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhantomLampBlockEntity entity) {
        // === Charging phase ===
        if (entity.chargeTicks > 0 && entity.chargeTicks < CHARGE_TIME) {
            Player player = entity.chargingPlayer != null ? level.getPlayerByUUID(entity.chargingPlayer) : null;

            boolean playerNearby = player != null &&
                    player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= CHARGE_DISTANCE_SQ;

            if (playerNearby) {
                entity.chargeTicks++;

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                            1, 0.2, 0.2, 0.2, 0.02);
                }

                if (entity.chargeTicks % 20 == 0) {
                    int secondsLeft = (CHARGE_TIME - entity.chargeTicks) / 20 + 1;
                    player.displayClientMessage(
                            Component.translatable("message.hasoook.phantom_lamp.charging", secondsLeft),
                            true);
                }
            } else {
                entity.chargeTicks = 0;
                entity.chargingPlayer = null;
                setChangedAndSync(entity);
            }
        }

        // Charge complete → trigger freeze
        if (entity.chargeTicks >= CHARGE_TIME && entity.freezeTicks == 0) {
            entity.chargeTicks = 0;
            entity.chargingPlayer = null;
            entity.freezeTicks = FREEZE_TIME;
            entity.applyFreezeEffect(level, pos);
            setChangedAndSync(entity);
        }

        // === Freeze phase ===
        if (entity.freezeTicks > 0) {
            entity.freezeTicks--;

            if (level instanceof ServerLevel serverLevel && entity.freezeTicks % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        3, 0.4, 0.4, 0.4, 0.05);
            }

            if (entity.freezeTicks > 0 && entity.freezeTicks % 20 == 0) {
                entity.applyFreezeEffect(level, pos);
            }

            if (entity.freezeTicks <= 0) {
                entity.selfDestruct(level, pos);
            }
        }

        // === Phantom Grab phase ===
        if (entity.phantomGrabTicks > 0) {
            // If the lamp block was destroyed, clean up immediately
            if (!(level.getBlockState(pos).getBlock() instanceof com.hasoook.hasoook.block.custom.PhantomLampBlock)) {
                entity.onBlockDestroyed();
                return;
            }
            entity.phantomGrabTicks--;

            if (level instanceof ServerLevel serverLevel) {
                for (GrabTask task : entity.grabTasks) {
                    if (task.discarded) continue;

                    Entity phantomEntity = serverLevel.getEntity(task.phantomUuid);
                    if (!(phantomEntity instanceof Phantom phantom) || !phantom.isAlive()) {
                        continue;
                    }

                    int elapsed = PHANTOM_GRAB_DURATION - entity.phantomGrabTicks;
                    int id = phantom.getId();

                    if (!task.grabbed) {
                        // Phase 1: Fly toward the target monster with organic wobble
                        Entity target = serverLevel.getEntity(task.targetUuid);
                        if (target != null && target.isAlive()) {
                            double dx = target.getX() - phantom.getX();
                            double dy = (target.getY() + target.getBbHeight()) - phantom.getY();
                            double dz = target.getZ() - phantom.getZ();
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                            if (dist < GRAB_DISTANCE) {
                                // Snatch the target — freeze it and let it dangle below
                                if (target instanceof Mob mob) {
                                    mob.setNoAi(true);
                                }
                                target.setNoGravity(true);
                                task.grabbed = true;
                                // Burst of particles on grab
                                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                        phantom.getX(), phantom.getY(), phantom.getZ(),
                                        10, 0.3, 0.3, 0.3, 0.08);
                            } else {
                                // Base direction toward target
                                double horizDist = Math.sqrt(dx * dx + dz * dz);
                                double forwardX = dx / dist;
                                double forwardY = dy / dist;
                                double forwardZ = dz / dist;

                                // Pulsing speed — each phantom has its own rhythm
                                double speed = APPROACH_SPEED + Math.sin(elapsed * 0.25 + id * 0.7) * 0.05;

                                // Side-to-side sway (perpendicular to forward in horizontal plane)
                                double swayFreq = 0.3 + (id % 5) * 0.05;
                                double sway = Math.sin(elapsed * swayFreq + id) * 0.08;
                                double perpX = horizDist > 0.01 ? -forwardZ / horizDist * dist : 0;
                                double perpZ = horizDist > 0.01 ? forwardX / horizDist * dist : 0;

                                // Vertical bob
                                double bobFreq = 0.4 + (id % 3) * 0.08;
                                double bob = Math.cos(elapsed * bobFreq + id * 0.5) * 0.05;

                                Vec3 mov = new Vec3(
                                        forwardX * speed + perpX * sway / dist,
                                        forwardY * speed + bob,
                                        forwardZ * speed + perpZ * sway / dist);

                                // Yaw: face the overall heading
                                float yaw = (float) Math.toDegrees(Math.atan2(forwardZ, forwardX)) - 90.0F;
                                phantom.setYRot(yaw);
                                phantom.yBodyRot = yaw;
                                // Pitch toward target (Phantom model: + = up)
                                float pitch = (float) Math.toDegrees(Math.atan2(dy, horizDist));
                                phantom.setXRot(pitch);

                                phantom.setDeltaMovement(mov);
                                phantom.move(MoverType.SELF, mov);
                            }
                        } else {
                            // Target died — dismiss this phantom
                            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    phantom.getX(), phantom.getY(), phantom.getZ(),
                                    8, 0.2, 0.2, 0.2, 0.05);
                            phantom.discard();
                            task.discarded = true;
                        }
                    } else {
                        // Phase 2: Organic spiral ascent with multi-layered variation
                        double progress = (double) elapsed / PHANTOM_GRAB_DURATION;
                        double ease = progress * progress;

                        // Base acceleration curve
                        double baseSpeed = ASCEND_SPEED_MIN + (ASCEND_SPEED_MAX - ASCEND_SPEED_MIN) * ease;
                        // Gentle speed breathing — each phantom at its own rhythm
                        double breathe = Math.sin(elapsed * 0.12 + id * 0.9) * 0.025;
                        double ascendSpeed = baseSpeed + breathe;

                        // Expanding spiral radius with organic variance
                        double baseRadius = SPIRAL_RADIUS_MAX * (0.1 + 0.9 * ease);
                        double radiusDrift = Math.cos(elapsed * 0.18 + id) * 0.4;
                        double radius = Math.max(0.1, baseRadius + radiusDrift);

                        // Spiral angle
                        double angle = elapsed * SPIRAL_ANGULAR_SPEED + id * 1.7;
                        double hSpeed = radius * SPIRAL_ANGULAR_SPEED;
                        double vx = -hSpeed * Math.sin(angle);
                        double vz = hSpeed * Math.cos(angle);

                        // Multi-layered vertical flutter
                        double flutter1 = Math.sin(elapsed * 0.3 + id * 1.1) * 0.03;
                        double flutter2 = Math.cos(elapsed * 0.5 + id * 0.6) * 0.02;
                        double flutter = flutter1 + flutter2;

                        Vec3 spiralMov = new Vec3(vx, ascendSpeed + flutter, vz);

                        // Face tangent direction with slight head bobbing
                        float yaw = (float) Math.toDegrees(Math.atan2(vz, vx)) - 90.0F;
                        phantom.setYRot(yaw);
                        phantom.yBodyRot = yaw;
                        float ascendPitch = 25.0F + (float) (ease * 30.0F) + (float) (flutter1 * 15.0F);
                        phantom.setXRot(ascendPitch);

                        phantom.setDeltaMovement(spiralMov);
                        phantom.move(MoverType.SELF, spiralMov);

                        // Make prey dangle below the phantom
                        Entity prey = serverLevel.getEntity(task.targetUuid);
                        if (prey != null && prey.isAlive()) {
                            double dangleX = phantom.getX() + Math.sin(elapsed * 0.4 + id) * 0.15;
                            // Phantom model is much larger than its hitbox — use generous offset
                            double gap = 0.9 + prey.getBbHeight() * 0.5 + 0.5;
                            double dangleY = phantom.getY() - gap + Math.cos(elapsed * 0.55 + id) * 0.08;
                            double dangleZ = phantom.getZ() + Math.cos(elapsed * 0.4 + id) * 0.15;
                            prey.setPos(dangleX, dangleY, dangleZ);
                            prey.setDeltaMovement(Vec3.ZERO);
                            prey.fallDistance = 0;
                        }

                        // Spiral ring particles — more frequent as speed increases
                        int particleInterval = ease > 0.5 ? 1 : 2;
                        if (entity.phantomGrabTicks % particleInterval == 0) {
                            double ringX = phantom.getX() - vx * 2.5;
                            double ringZ = phantom.getZ() - vz * 2.5;
                            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    ringX, phantom.getY(), ringZ,
                                    1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }

                    // Trailing particles
                    if (entity.phantomGrabTicks % 3 == 0) {
                        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                phantom.getX(), phantom.getY(), phantom.getZ(),
                                1, 0.15, 0.1, 0.15, 0.02);
                    }
                }
            }

            if (entity.phantomGrabTicks <= 0) {
                // Time's up — remove all phantoms, release prey to fall
                if (level instanceof ServerLevel serverLevel) {
                    for (GrabTask task : entity.grabTasks) {
                        if (task.discarded) continue;
                        // Release prey — restore gravity so it falls
                        Entity prey = serverLevel.getEntity(task.targetUuid);
                        if (prey != null) {
                            prey.setNoGravity(false);
                            if (prey instanceof Mob mob) {
                                mob.setNoAi(false);
                            }
                        }
                        Entity phantomEntity = serverLevel.getEntity(task.phantomUuid);
                        if (phantomEntity != null) {
                            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    phantomEntity.getX(), phantomEntity.getY(), phantomEntity.getZ(),
                                    20, 0.6, 0.6, 0.6, 0.2);
                            phantomEntity.discard();
                        }
                    }
                }
                entity.grabTasks.clear();
                setChangedAndSync(entity);
            }
        }
    }

    private void applyFreezeEffect(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            AABB area = new AABB(pos).inflate(EFFECT_RADIUS);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                    e -> true);

            for (LivingEntity entity : entities) {
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 210, 255,
                        false, false, true));
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 210, 255,
                        false, false, true));
                entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 210, 255,
                        false, false, true));
            }
        }
    }

    private void selfDestruct(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack lampStack = new ItemStack(ModBlocks.PHANTOM_LAMP.get().asItem());
            PhantomLampBlockItem.setState(lampStack, PhantomLampBlockItem.STATE_BROKEN);
            ItemEntity itemEntity = new ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lampStack);
            level.addFreshEntity(itemEntity);

            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    15, 0.5, 0.5, 0.5, 0.1);
        }
        level.removeBlock(pos, false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.putString("LampState", lampState);
        output.putInt("ChargeTicks", chargeTicks);
        output.putInt("FreezeTicks", freezeTicks);
        output.putInt("PhantomGrabTicks", phantomGrabTicks);
        if (chargingPlayer != null) {
            output.putString("ChargingPlayer", chargingPlayer.toString());
        }
        if (!grabTasks.isEmpty()) {
            output.putString("GrabPhantomUuids", grabTasks.stream()
                    .map(t -> t.phantomUuid.toString())
                    .collect(Collectors.joining(",")));
            output.putString("GrabTargetUuids", grabTasks.stream()
                    .map(t -> t.targetUuid.toString())
                    .collect(Collectors.joining(",")));
            output.putString("GrabGrabbed", grabTasks.stream()
                    .map(t -> t.grabbed ? "1" : "0")
                    .collect(Collectors.joining(",")));
            output.putString("GrabDiscarded", grabTasks.stream()
                    .map(t -> t.discarded ? "1" : "0")
                    .collect(Collectors.joining(",")));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        lampState = input.getStringOr("LampState", PhantomLampBlockItem.STATE_PRISTINE);
        chargeTicks = input.getIntOr("ChargeTicks", 0);
        freezeTicks = input.getIntOr("FreezeTicks", 0);
        phantomGrabTicks = input.getIntOr("PhantomGrabTicks", 0);
        String uuidStr = input.getStringOr("ChargingPlayer", "");
        chargingPlayer = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);
        grabTasks.clear();
        String phantomUuidsStr = input.getStringOr("GrabPhantomUuids", "");
        String targetUuidsStr = input.getStringOr("GrabTargetUuids", "");
        String grabbedStr = input.getStringOr("GrabGrabbed", "");
        String discardedStr = input.getStringOr("GrabDiscarded", "");
        if (!phantomUuidsStr.isEmpty() && !targetUuidsStr.isEmpty()) {
            String[] phantomUuids = phantomUuidsStr.split(",");
            String[] targetUuids = targetUuidsStr.split(",");
            String[] grabbedFlags = grabbedStr.isEmpty() ? new String[0] : grabbedStr.split(",");
            String[] discardedFlags = discardedStr.isEmpty() ? new String[0] : discardedStr.split(",");
            for (int i = 0; i < phantomUuids.length && i < targetUuids.length; i++) {
                GrabTask task = new GrabTask(
                        UUID.fromString(phantomUuids[i]),
                        UUID.fromString(targetUuids[i]));
                if (i < grabbedFlags.length) {
                    task.grabbed = "1".equals(grabbedFlags[i]);
                }
                if (i < discardedFlags.length) {
                    task.discarded = "1".equals(discardedFlags[i]);
                }
                grabTasks.add(task);
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static void setChangedAndSync(PhantomLampBlockEntity entity) {
        entity.setChangedAndSync();
    }
}

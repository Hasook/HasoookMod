package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class CardProjectile extends ThrowableItemProjectile {
    private float damage = 2.0F;
    private boolean stuck = false;
    private int stuckTick = 0;
    private Vec3 stuckVelocity = Vec3.ZERO;

    // 出千附魔：自动索敌
    private static final double HOMING_RANGE = 16.0;
    private static final double MAX_TURN_RADIANS = Math.toRadians(3.0); // 每 tick 最多转 3°
    private static final double HOMING_CONE_DOT = 0.5;                   // cos(60°) 只追踪前方锥形

    // 必须通过 EntityData 同步，否则客户端不知道要跑索敌逻辑
    private static final EntityDataAccessor<Boolean> DATA_HAS_CHEATING =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_BOMB =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_CURVE_TX =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURVE_TY =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURVE_TZ =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURVE_RATE =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CURVE_DELAY =
            SynchedEntityData.defineId(CardProjectile.class, EntityDataSerializers.INT);

    private LivingEntity lockedTarget = null;

    private static final int TRAIL_LENGTH = 8;
    private static final int STUCK_LIFETIME = 15;
    private final Vec3[] trail = new Vec3[TRAIL_LENGTH];
    private int trailIndex = 0;
    private int trailCount = 0;

    public CardProjectile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public CardProjectile(LivingEntity shooter, Level level) {
        super(ModEntities.CARD_PROJECTILE.get(), level);
        this.setOwner(shooter);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HAS_CHEATING, false);
        builder.define(DATA_IS_BOMB, false);
        builder.define(DATA_CURVE_TX, 0f);
        builder.define(DATA_CURVE_TY, 0f);
        builder.define(DATA_CURVE_TZ, 0f);
        builder.define(DATA_CURVE_RATE, 0f);
        builder.define(DATA_CURVE_DELAY, 0);
    }

    public void setDamage(float d) { this.damage = d; }

    public void setHasCheating(boolean v) {
        this.entityData.set(DATA_HAS_CHEATING, v);
    }

    public void setBomb(boolean v) {
        this.entityData.set(DATA_IS_BOMB, v);
    }

    /** 设置弧线: 先横飞 delay tick, 再每tick向原始方向恢复 */
    public void setCurve(Vec3 forwardDir, double rate, int delay) {
        Vec3 d = forwardDir.normalize();
        this.entityData.set(DATA_CURVE_TX, (float) d.x);
        this.entityData.set(DATA_CURVE_TY, (float) d.y);
        this.entityData.set(DATA_CURVE_TZ, (float) d.z);
        this.entityData.set(DATA_CURVE_RATE, (float) rate);
        this.entityData.set(DATA_CURVE_DELAY, delay);
    }

    public boolean isBomb() {
        return this.entityData.get(DATA_IS_BOMB);
    }

    public boolean isStuck() { return stuck; }
    public int getStuckTick() { return stuckTick; }
    public Vec3 getStuckVelocity() { return stuckVelocity; }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide() && isBomb()) {
            this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3.0F, Level.ExplosionInteraction.MOB);
            discard();
            return;
        }
        this.stuckVelocity = this.getDeltaMovement();
        this.stuck = true;
        this.setDeltaMovement(0, 0, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!stuck) {
            trail[trailIndex] = this.position();
            trailIndex = (trailIndex + 1) % TRAIL_LENGTH;
            if (trailCount < TRAIL_LENGTH) trailCount++;

            if (this.entityData.get(DATA_HAS_CHEATING)) {
                applyHoming();
            }

            // 弧线: 先延迟横飞, 再lerp回正
            int cDelay = this.entityData.get(DATA_CURVE_DELAY);
            if (cDelay > 0) {
                this.entityData.set(DATA_CURVE_DELAY, cDelay - 1);
            } else {
                float cRate = this.entityData.get(DATA_CURVE_RATE);
                if (cRate > 0) {
                    Vec3 target = new Vec3(
                            this.entityData.get(DATA_CURVE_TX),
                            this.entityData.get(DATA_CURVE_TY),
                            this.entityData.get(DATA_CURVE_TZ));
                    Vec3 vel = this.getDeltaMovement();
                    double speed = vel.length();
                    if (speed > 0.1 && target.lengthSqr() > 0.01) {
                        Vec3 cur = vel.normalize();
                        Vec3 newDir = cur.lerp(target, cRate).normalize();
                        this.setDeltaMovement(newDir.scale(speed));
                    }
                }
            }
        }
        if (stuck && ++stuckTick > STUCK_LIFETIME) discard();
    }

    private void applyHoming() {
        // 粘性锁定：除非目标失效，否则不换
        if (lockedTarget != null && (!lockedTarget.isAlive() || !isValidTarget(lockedTarget))) {
            lockedTarget = null;
        }
        if (lockedTarget == null) {
            lockedTarget = findHomingTarget();
        }
        if (lockedTarget == null) return;

        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 0.1) return;

        Vec3 currentDir = velocity.normalize();
        Vec3 toTarget = lockedTarget.getBoundingBox().getCenter()
                .subtract(this.getBoundingBox().getCenter());
        if (toTarget.lengthSqr() < 0.0001) return;
        toTarget = toTarget.normalize();

        double dot = Mth.clamp(currentDir.dot(toTarget), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 0.001) return;

        // slerp：每 tick 最多旋转 MAX_TURN_RADIANS
        double turn = Math.min(angle, MAX_TURN_RADIANS);
        double sinA = Math.sin(angle);
        // 防止目标在正后方（≈180°）时 sinA≈0 导致 NaN
        if (sinA < 1e-6) return;
        Vec3 newDir = currentDir.scale(Math.sin(angle - turn) / sinA)
                .add(toTarget.scale(Math.sin(turn) / sinA))
                .normalize();
        this.setDeltaMovement(newDir.scale(speed));
    }

    @javax.annotation.Nullable
    /** 目标是否仍然有效 (在范围内且在前方锥形内) */
    private boolean isValidTarget(LivingEntity target) {
        if (target.distanceToSqr(this) > HOMING_RANGE * HOMING_RANGE) return false;
        Vec3 dir = this.getDeltaMovement().normalize();
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position()).normalize();
        return dir.dot(toTarget) >= HOMING_CONE_DOT;
    }

    private LivingEntity findHomingTarget() {
        Vec3 pos = this.position();
        Vec3 dir = this.getDeltaMovement().normalize();
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;
        LivingEntity closest = null;
        double closestDist = HOMING_RANGE * HOMING_RANGE;

        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(pos, pos).inflate(HOMING_RANGE))) {
            if (entity == owner) continue;
            if (!entity.isAlive()) continue;
            // 只追踪前方锥形内的生物
            Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(pos).normalize();
            if (dir.dot(toTarget) < HOMING_CONE_DOT) continue;
            double dist = entity.distanceToSqr(this);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }

    public Vec3[] getTrailPositions() {
        if (trailCount < 2) return new Vec3[0];
        Vec3[] result = new Vec3[trailCount + 1];
        int start = trailCount < TRAIL_LENGTH ? 0 : trailIndex;
        for (int i = 0; i < trailCount; i++) {
            result[i] = trail[(start + i) % TRAIL_LENGTH];
        }
        result[trailCount] = this.position();
        return result;
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Stuck", Codec.BOOL, stuck);
        output.store("StuckTick", Codec.INT, stuckTick);
        output.store("StuckVelocity", Vec3.CODEC, stuckVelocity);
        output.store("HasCheating", Codec.BOOL, entityData.get(DATA_HAS_CHEATING));
        output.store("IsBomb", Codec.BOOL, entityData.get(DATA_IS_BOMB));
        output.store("CurveTX", Codec.FLOAT, entityData.get(DATA_CURVE_TX));
        output.store("CurveTY", Codec.FLOAT, entityData.get(DATA_CURVE_TY));
        output.store("CurveTZ", Codec.FLOAT, entityData.get(DATA_CURVE_TZ));
        output.store("CurveRate", Codec.FLOAT, entityData.get(DATA_CURVE_RATE));
        output.store("CurveDelay", Codec.INT, entityData.get(DATA_CURVE_DELAY));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        stuck = input.read("Stuck", Codec.BOOL).orElse(false);
        stuckTick = input.read("StuckTick", Codec.INT).orElse(0);
        stuckVelocity = input.read("StuckVelocity", Vec3.CODEC).orElse(Vec3.ZERO);
        boolean hc = input.read("HasCheating", Codec.BOOL).orElse(false);
        this.entityData.set(DATA_HAS_CHEATING, hc);
        boolean bomb = input.read("IsBomb", Codec.BOOL).orElse(false);
        this.entityData.set(DATA_IS_BOMB, bomb);
        this.entityData.set(DATA_CURVE_TX, input.read("CurveTX", Codec.FLOAT).orElse(0f));
        this.entityData.set(DATA_CURVE_TY, input.read("CurveTY", Codec.FLOAT).orElse(0f));
        this.entityData.set(DATA_CURVE_TZ, input.read("CurveTZ", Codec.FLOAT).orElse(0f));
        this.entityData.set(DATA_CURVE_RATE, input.read("CurveRate", Codec.FLOAT).orElse(0f));
        this.entityData.set(DATA_CURVE_DELAY, input.read("CurveDelay", Codec.INT).orElse(0));
    }

    @Override
    protected @NonNull Item getDefaultItem() {
        return ModItems.CARD_PROJECTILE.get();
    }

    @Override
    protected void onHitEntity(@NonNull EntityHitResult result) {
        super.onHitEntity(result);
        if (result.getEntity() == getOwner()) return;
        if (!level().isClientSide()) {
            // 清除受伤无敌帧，使多牌齐射每张都造成伤害
            if (result.getEntity() instanceof LivingEntity le)
                le.invulnerableTime = 0;
            result.getEntity().hurt(this.damageSources().thrown(this, getOwner()), damage);
            if (isBomb()) {
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3.0F, Level.ExplosionInteraction.MOB);
            }
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.2F);
        }
        discard();
    }
}

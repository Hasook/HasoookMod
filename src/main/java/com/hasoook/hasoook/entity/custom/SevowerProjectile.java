package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class SevowerProjectile extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> ID_RETURNING = SynchedEntityData.defineId(SevowerProjectile.class, EntityDataSerializers.BOOLEAN);

    private float damage = 8.0F;

    public SevowerProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true); // 【新增】保证客户端生成时也没有重力
    }

    public SevowerProjectile(LivingEntity shooter, Level level, ItemStack stack) {
        super(ModEntities.SEVOWER.get(), shooter, level, stack, null);

        this.setNoGravity(true); // 【新增】全程无视重力

        if (shooter instanceof Player player && player.getAbilities().instabuild) {
            this.pickup = Pickup.CREATIVE_ONLY;
        } else {
            this.pickup = Pickup.ALLOWED;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_RETURNING, false);
    }

    public boolean isReturning() {
        return this.entityData.get(ID_RETURNING);
    }

    public void setReturning(boolean returning) {
        this.entityData.set(ID_RETURNING, returning);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected @NonNull ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.SEVOWER.get());
    }

    public ItemStack getItem() {
        return this.getPickupItem();
    }

    @Override
    public void tick() {
        Entity owner = this.getOwner();

        if ((this.inGroundTime > 4 || this.tickCount > 15) && !this.isReturning()) {
            if (!this.level().isClientSide()) {
                this.setReturning(true);
            }
        }

        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide() && this.pickup == Pickup.ALLOWED) {
                this.spawnAtLocation((ServerLevel) this.level(), this.getPickupItem(), 0.1F);
                this.discard();
            }
            super.tick();
            return;
        }

        if (this.isReturning()) {
            this.setNoPhysics(true);

            // 【新增】返回穿墙状态下，原版碰撞射线会失效，因此手动进行范围检测造成伤害
            if (!this.level().isClientSide()) {
                for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.5))) {
                    if (target != owner) {
                        // Minecraft自带原版无敌帧(hurtResistantTime)，因此不用担心一瞬间造成几十次伤害
                        target.hurt(this.damageSources().thrown(this, owner), damage);
                    }
                }
            }

            Vec3 eyePos = owner.getEyePosition();
            Vec3 toOwner = eyePos.subtract(this.position());

            this.setPosRaw(this.getX(), this.getY() + toOwner.y * 0.015, this.getZ());
            if (!this.level().isClientSide()) {
                double speed = 0.15;
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95).add(toOwner.normalize().scale(speed)));
            }
        }

        super.tick();
    }

    @Override
    public void playerTouch(@NonNull Player player) {
        // 只有扔出它的主人才能捡起它（或者没有主人）
        if (this.level().isClientSide() || (!this.ownedBy(player) && this.getOwner() != null)) {
            return;
        }

        if (this.isReturning()) {
            boolean pickedUp = false;

            // 尝试放入背包或创造模式直接销毁
            if (this.pickup == Pickup.ALLOWED) {
                pickedUp = player.getInventory().add(this.getPickupItem());
            } else if (this.pickup == Pickup.CREATIVE_ONLY) {
                pickedUp = true;
            }

            if (pickedUp) {
                // 放入背包成功，播放拾取动画/声音，并销毁实体
                player.take(this, 1);
                this.discard();
            } else {
                // 确实是背包满了放不进去，生成掉落物并销毁实体
                if (this.pickup == Pickup.ALLOWED && !this.isRemoved()) {
                    this.spawnAtLocation((ServerLevel) this.level(), this.getPickupItem(), 0.1F);
                }
                this.discard();
            }
        } else {
            // 如果还没进入返回状态（比如飞在半空中碰到），就走原版正常的拾取判定
            super.playerTouch(player);
        }
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || (this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        Entity owner = this.getOwner();
        if (hit == owner) return;

        if (!this.level().isClientSide()) {
            hit.hurt(this.damageSources().thrown(this, owner), damage);

            // 【修改】删除了击中后反弹减速和直接 setReturning(true) 的逻辑
            // 使得武器会直接穿透生物继续飞行，直到持续时间达到 20 ticks（即 1 秒）或者撞墙才会回来
        }
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult result) {

    }

    @Override
    protected @NonNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT;
    }
}
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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class HeavyHalberdProjectile extends AbstractArrow {

    /** 闪电链搜索半径 */
    private static final double CHAIN_RANGE = 8.0;
    /** 闪电链基础伤害 */
    private static final float CHAIN_DAMAGE = 6.0F;
    /** 击中后闪电链持续渲染的 tick 数 */
    private static final int CHAIN_DURATION_TICKS = 10;

    /** 同步到客户端的闪电链剩余 tick 数，> 0 时渲染器会绘制闪电链 */
    private static final EntityDataAccessor<Integer> DATA_CHAIN_TICKS =
            SynchedEntityData.defineId(HeavyHalberdProjectile.class, EntityDataSerializers.INT);

    public HeavyHalberdProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public HeavyHalberdProjectile(LivingEntity shooter, Level level, ItemStack stack) {
        super(ModEntities.HEAVY_HALBERD.get(), shooter, level, stack, null);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHAIN_TICKS, 0);
    }

    @Override
    protected @NonNull ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.HEAVY_HALBERD.get());
    }

    @Override
    protected @NonNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    public ItemStack getItem() {
        return this.getPickupItem();
    }

    /** 闪电链剩余 tick，供渲染器查询。 */
    public int getChainTicks() {
        return this.entityData.get(DATA_CHAIN_TICKS);
    }

    private void setChainTicks(int ticks) {
        this.entityData.set(DATA_CHAIN_TICKS, ticks);
    }

    @Override
    public void tick() {
        super.tick();

        // 服务端倒计时闪电链持续时间
        if (this.level() instanceof ServerLevel) {
            int remaining = getChainTicks();
            if (remaining > 0) {
                setChainTicks(remaining - 1);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // 父类处理箭的直击伤害、击退等（不会 discard 非穿透箭）
        super.onHitEntity(result);
        triggerLightningChain(result.getEntity().position());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        triggerLightningChain(result.getLocation());
    }

    /**
     * 以击中位置为中心，对周围生物造成闪电伤害，并激活客户端闪电视觉链。
     */
    private void triggerLightningChain(Vec3 center) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Entity owner = this.getOwner();

        // 搜索范围内的所有生物
        List<LivingEntity> targets = this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(CHAIN_RANGE),
                e -> e.isAlive() && e != owner
        );

        // 对每个目标造成闪电伤害
        for (LivingEntity target : targets) {
            target.hurt(target.damageSources().lightningBolt(), CHAIN_DAMAGE);

            // 直接命中的实体额外伤害
            if (target.position().distanceTo(center) < 2.0) {
                target.hurt(target.damageSources().lightningBolt(), 2.0F);
            }
        }

        // 激活客户端闪电链渲染
        setChainTicks(CHAIN_DURATION_TICKS);
    }
}

package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.entity.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.NonNull;

public class AmethystShardProjectile extends ThrowableItemProjectile {
    private float damage = 1.0F; // 默认伤害

    public AmethystShardProjectile(EntityType<? extends ThrowableItemProjectile> p_480397_, Level p_479814_) {
        super(p_480397_, p_479814_);
    }

    public AmethystShardProjectile(LivingEntity shooter, Level level) {
        super(ModEntities.AMETHYST_SHARD.get(), level);
    }

    @Override
    protected @NonNull Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected void onHitEntity(@NonNull EntityHitResult result) {
        super.onHitEntity(result);

        Entity entity = result.getEntity();
        if (!this.level().isClientSide()) {
            ServerLevel level = (ServerLevel) this.level();

            // 使用设置的伤害，保证至少 1 点
            entity.hurt(this.damageSources().thrown(this, this.getOwner()), Math.max(1.0F, this.damage));

            // 紫水晶簇粒子
            BlockState state = Blocks.AMETHYST_CLUSTER.defaultBlockState();
            for (int i = 0; i < 8; i++) {
                double dx = (level.random.nextDouble() - 0.5) * 0.2;
                double dy = (level.random.nextDouble() - 0.5) * 0.2;
                double dz = (level.random.nextDouble() - 0.5) * 0.2;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        this.getX(), this.getY() + 0.1, this.getZ(), 1, dx, dy, dz, 0.0);
            }

            if (this.damage > 40.0F) {
                // 末影水晶爆炸
                level.explode(
                        this.getOwner(),
                        null,
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        1.0F,
                        false,
                        Level.ExplosionInteraction.BLOCK
                );
            }

            // 音效
            level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);

            if (this.damage < 40.0F) {
                this.discard();
            }
        }
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult result) {
        if (!this.level().isClientSide()) {
            var level = (ServerLevel) this.level();

            // 紫水晶簇破坏粒子
            BlockState state = Blocks.AMETHYST_CLUSTER.defaultBlockState();

            for (int i = 0; i < 8; i++) {
                double dx = (level.random.nextDouble() - 0.5) * 0.2;
                double dy = (level.random.nextDouble() - 0.5) * 0.2;
                double dz = (level.random.nextDouble() - 0.5) * 0.2;

                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        this.getX(),
                        this.getY() + 0.1,
                        this.getZ(),
                        1,
                        dx,
                        dy,
                        dz,
                        0.0
                );
            }

            if (this.damage > 40.0F) {
                // 末影水晶爆炸
                level.explode(
                        this.getOwner(),
                        null,
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        1.0F,
                        false,
                        Level.ExplosionInteraction.BLOCK
                );
            }

            // 音效
            level.playSound(
                    null,
                    this.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.PLAYERS,
                    1.0F,
                    0.8F + level.random.nextFloat() * 0.3F
            );
            this.discard();
        }
        super.onHitBlock(result);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level && this.damage > 40.0F) {
            level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(), 2, 0.0D, 0.0D, 0.0D, 0.2D);
            level.sendParticles(ParticleTypes.WITCH, this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.2D);
        }
        super.tick();
    }
}
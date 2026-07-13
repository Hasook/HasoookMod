package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.SocksItem;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;

public class ThrownSockEntity extends ThrowableItemProjectile {

    private int wear = 0;
    private int particleTimer = 0;

    /// 粒子配置（与脚穿袜子一致）
    private static final int[] POISON_COLORS = {
            0x000000, 0xFF7BA05B, 0xFF5A8A3C, 0xFF3D6B22, 0xFF254D0F, 0xFF123004, 0xFF061801,
    };
    private static final int[] PARTICLE_INTERVAL = {
            999, 20, 12, 8, 5, 3, 2,
    };
    private static final int[] PARTICLE_COUNT = {
            0, 1, 1, 1, 2, 2, 3,
    };

    public ThrownSockEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ThrownSockEntity(LivingEntity shooter, Level level) {
        super(ModEntities.THROWN_SOCK.get(), level);
        this.setOwner(shooter);
    }

    public void setWear(int wear) {
        this.wear = wear;
    }

    @Override
    protected @NonNull Item getDefaultItem() {
        return ModItems.SOCK.get();
    }

    @Override
    public @NonNull ItemStack getItem() {
        ItemStack stack = new ItemStack(ModItems.SOCK.get());
        if (wear > 0) {
            stack.set(ModDataComponents.SOCKS_WEAR.get(), wear);
        }
        return stack;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (this.level().isClientSide()) return;

        int wearStage = SocksItem.getStage(wear);

        if (hit instanceof ServerPlayer player) {
            // 玩家 → 糊脸 + 中毒（tick 中处理）
            int texIndex = ThreadLocalRandom.current().nextInt(3);
            int seed = ThreadLocalRandom.current().nextInt(16);
            int packed = (texIndex << 24) | (wearStage << 16) | (seed << 12) | 2047;
            String curr = hit.getData(ModAttachments.SOCK_FACE.get());
            String next = curr.isEmpty() ? String.valueOf(packed) : curr + "," + packed;
            hit.setData(ModAttachments.SOCK_FACE.get(), next);
        } else if (hit instanceof LivingEntity target && wearStage >= 4) {
            // 非玩家 → 直接给予中毒
            int amplifier = wearStage - 4;
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 400, amplifier,
                    false, true));
        }

        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;

        int stage = SocksItem.getStage(wear);
        if (stage <= 0) return;

        particleTimer++;
        int interval = PARTICLE_INTERVAL[stage];
        if (particleTimer >= interval) {
            particleTimer = 0;
            int count = PARTICLE_COUNT[stage];
            ((ServerLevel) this.level()).sendParticles(
                    ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, POISON_COLORS[stage]),
                    this.getX(), this.getY() + 0.25, this.getZ(),
                    count, 0.2, 0.1, 0.2, 0.02);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide()) {
            // 先掉落物品，再丢弃（不能反着来，否则丢弃后 spawnAtLocation 不生效）
            ItemStack drop = new ItemStack(ModItems.SOCK.get());
            if (wear > 0) {
                drop.set(ModDataComponents.SOCKS_WEAR.get(), wear);
            }
            this.spawnAtLocation((ServerLevel) this.level(), drop);
        }
        this.discard();
    }
}

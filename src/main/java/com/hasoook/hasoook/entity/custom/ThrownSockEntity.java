package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.component.SockFaceData;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.SocksItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

import java.util.concurrent.ThreadLocalRandom;

public class ThrownSockEntity extends ThrownItemEntity {

    private int wear = 0;
    private int particleTimer = 0;

    private static final int[] POISON_COLORS = {
            0x000000, 0xFF7BA05B, 0xFF5A8A3C, 0xFF3D6B22, 0xFF254D0F, 0xFF123004, 0xFF061801,
    };
    private static final int[] PARTICLE_INTERVAL = {999, 20, 12, 8, 5, 3, 2};
    private static final int[] PARTICLE_COUNT = {0, 1, 1, 1, 2, 2, 3};

    public ThrownSockEntity(EntityType<? extends ThrownItemEntity> entityType, World world) {
        super(entityType, world);
    }

    public ThrownSockEntity(LivingEntity owner, World world) {
        super(ModEntities.THROWN_SOCK, owner, world, new ItemStack(ModItems.SOCK));
    }

    public void setWear(int wear) {
        this.wear = wear;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SOCK;
    }

    @Override
    public ItemStack getStack() {
        ItemStack stack = new ItemStack(ModItems.SOCK);
        if (wear > 0) {
            stack.set(ModDataComponents.SOCKS_WEAR, wear);
        }
        return stack;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity hit = entityHitResult.getEntity();
        if (this.getEntityWorld().isClient()) return;

        int wearStage = SocksItem.getStage(wear);

        if (hit instanceof ServerPlayerEntity player) {
            int texIndex = ThreadLocalRandom.current().nextInt(3);
            int seed = ThreadLocalRandom.current().nextInt(16);
            int packed = (texIndex << 24) | (wearStage << 16) | (seed << 12) | 2047;
            String curr = SockFaceData.getSockFace(hit);
            String next = curr.isEmpty() ? String.valueOf(packed) : curr + "," + packed;
            SockFaceData.setSockFace(hit, next);
            // 立即同步到客户端
            com.hasoook.hasoook.network.ModNetworkInit.syncSockFaceToPlayer(player);
        } else if (hit instanceof LivingEntity target && wearStage >= 4) {
            int amplifier = wearStage - 4;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 400, amplifier,
                    false, true));
        }

        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getEntityWorld().isClient()) return;

        int stage = SocksItem.getStage(wear);
        if (stage <= 0) return;

        particleTimer++;
        int interval = PARTICLE_INTERVAL[stage];
        if (particleTimer >= interval) {
            particleTimer = 0;
            int count = PARTICLE_COUNT[stage];
            ServerWorld world = (ServerWorld) this.getEntityWorld();
            world.spawnParticles(
                    TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, POISON_COLORS[stage]),
                    this.getX(), this.getY() + 0.25, this.getZ(),
                    count, 0.2, 0.1, 0.2, 0.02);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (!this.getEntityWorld().isClient()) {
            ItemStack drop = new ItemStack(ModItems.SOCK);
            if (wear > 0) {
                drop.set(ModDataComponents.SOCKS_WEAR, wear);
            }
            this.dropStack((ServerWorld) this.getEntityWorld(), drop, 0.5f);
        }
        this.discard();
    }
}

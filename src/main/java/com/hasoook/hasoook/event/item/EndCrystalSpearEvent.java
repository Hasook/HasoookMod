package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.AmethystShardProjectile;
import com.hasoook.hasoook.item.custom.EndCrystalSpearItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class EndCrystalSpearEvent {

    // 记录每个玩家上一次触发效果的 tick，避免同一 tick 多次触发（例如横扫多目标）
    private static final Map<UUID, Integer> LAST_TRIGGER_TICK = new HashMap<>();

    @SubscribeEvent
    public static void onPostDamage(LivingDamageEvent.Post event) {
        // 只有攻击来源是玩家实体才处理
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        // 主手必须是末影水晶长矛
        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof EndCrystalSpearItem)) return;

        LivingEntity target = event.getEntity();
        Level level = target.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        int currentTick = serverLevel.getServer().getTickCount();
        UUID attackerId = attacker.getUUID();
        Integer lastTick = LAST_TRIGGER_TICK.get(attackerId);
        if (lastTick != null && lastTick == currentTick) {
            return; // 本 tick 已经触发过，跳过
        }
        LAST_TRIGGER_TICK.put(attackerId, currentTick);

        stack = attacker.getMainHandItem(); // 重新获取最新物品状态
        if (!(stack.getItem() instanceof EndCrystalSpearItem) || stack.isEmpty()) {
            return;
        }

        float bonusDamage = getDifficultyDamage(level.getDifficulty());
        RandomSource random = level.getRandom();
        Vec3 origin = target.position().add(0, target.getBbHeight() * 0.6, 0);

        // 音效
        level.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                2F, 0.7F + random.nextFloat() * 0.4F);
        level.playSound(null, target.blockPosition(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS,
                2F, 0.7F + random.nextFloat() * 0.4F);

        // 末影水晶爆炸
        level.explode(
                attacker,
                null,
                null,
                target.getX(), target.getY(), target.getZ(),
                6.0F,
                false,
                Level.ExplosionInteraction.BLOCK
        );

        // 随机发射紫水晶碎片
        for (int i = 0; i < 100; i++) {
            Vec3 direction = new Vec3(
                    random.nextDouble() - 0.5,
                    random.nextDouble() * 0.4,
                    random.nextDouble() - 0.5
            ).normalize();
            direction = clampUpward(direction);
            spawnShard(serverLevel, attacker, origin, direction,
                    0.8F + random.nextFloat() * 0.4F, bonusDamage);
        }

        // 播放额外音效
        serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                1.4F, 0.6F + random.nextFloat() * 0.4F);

        // 粒子特效
        spawnEndCrystalParticles(serverLevel, origin);

        // 耐久消耗
        stack.hurtAndBreak(stack.getMaxDamage(), attacker,
                attacker.getUsedItemHand().asEquipmentSlot());
    }

    private static Vec3 clampUpward(Vec3 dir) {
        if (dir.y > 0.5) {
            return new Vec3(dir.x, 0.5, dir.z).normalize();
        }
        return dir;
    }

    private static void spawnShard(ServerLevel level, LivingEntity owner,
                                   Vec3 origin, Vec3 direction,
                                   float speed, float damage) {
        AmethystShardProjectile shard = new AmethystShardProjectile(owner, level);
        shard.setPos(origin.x, origin.y, origin.z);
        shard.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        shard.setDamage(damage);
        level.addFreshEntity(shard);
    }

    private static float getDifficultyDamage(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL, EASY -> 43.5F;
            case NORMAL -> 85.0F;
            case HARD -> 127.5F;
        };
    }

    private static void spawnEndCrystalParticles(ServerLevel level, Vec3 center) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 40; i++) {
            double dx = random.nextGaussian() * 0.25;
            double dy = random.nextGaussian() * 0.25;
            double dz = random.nextGaussian() * 0.25;
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    center.x, center.y, center.z,
                    1, dx, dy, dz, 0.5
            );
        }
    }
}
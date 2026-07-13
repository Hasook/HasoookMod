package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.entity.custom.AmethystShardProjectile;
import com.hasoook.hasoook.item.custom.AmethystShardSpearItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class AmethystShardSpearEvent {
    @SubscribeEvent
    public static void onPostDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof AmethystShardSpearItem)) return;

        LivingEntity target = event.getEntity();
        Level level = target.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        RandomSource random = level.getRandom();

        Vec3 origin = target.position().add(0, target.getBbHeight() * 0.6, 0);
        float damage = Math.max(1.0F, event.getNewDamage()); // 实际造成伤害，最低1

        // 生成碎片逻辑
        List<LivingEntity> nearbyTargets = new ArrayList<>(serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(10.0),
                e -> e.isAlive() && e != attacker && e != target
        ));

        int baseCount = 3;
        int extra = Math.max(0, nearbyTargets.size() - 2);
        int totalCount = Math.min(10, baseCount + extra);

        int aimedCount = Math.min(nearbyTargets.size(), totalCount - 1);

        // 指向目标碎片
        for (int i = 0; i < aimedCount; i++) {
            int index = random.nextInt(nearbyTargets.size());
            LivingEntity aim = nearbyTargets.remove(index);

            Vec3 aimDir = aim.position().add(0, aim.getBbHeight() * 0.5, 0).subtract(origin).normalize();

            double yaw = (random.nextDouble() - 0.5) * Math.toRadians(30);
            double pitch = (random.nextDouble() - 0.5) * Math.toRadians(15);
            Vec3 direction = aimDir.yRot((float) yaw).xRot((float) pitch).normalize();
            direction = clampUpward(direction);

            spawnShard(serverLevel, attacker, origin, direction, 1.1F + random.nextFloat() * 0.2F, damage);
        }

        // 随机碎片
        int randomCount = totalCount - aimedCount;
        for (int i = 0; i < randomCount; i++) {
            Vec3 direction = new Vec3(random.nextDouble() - 0.5,
                    random.nextDouble() * 0.25,
                    random.nextDouble() - 0.5).normalize();
            direction = clampUpward(direction);

            spawnShard(serverLevel, attacker, origin, direction, 0.6F + random.nextFloat() * 0.3F, damage);
        }

        // 耐久消耗
        stack.hurtAndBreak(totalCount - 1, attacker, attacker.getUsedItemHand().asEquipmentSlot());

        // 音效
        serverLevel.playSound(null, target.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                1.2F, 0.7F + random.nextFloat() * 0.4F);
    }

    private static Vec3 clampUpward(Vec3 dir) {
        if (dir.y > 0.35) return new Vec3(dir.x, 0.35, dir.z).normalize();
        return dir;
    }

    private static void spawnShard(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 direction, float speed, float damage) {
        AmethystShardProjectile shard = new AmethystShardProjectile(owner, level);
        shard.setPos(origin.x, origin.y, origin.z);
        shard.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        shard.setDamage(damage);
        level.addFreshEntity(shard);
    }
}

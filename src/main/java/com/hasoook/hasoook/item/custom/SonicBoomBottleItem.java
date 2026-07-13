package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class SonicBoomBottleItem extends Item {

    public SonicBoomBottleItem(Properties properties) {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(16));
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, Player player, @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return super.use(level, player, hand);
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        if (level.isClientSide()) return false;

        ServerLevel serverLevel = (ServerLevel) level;

        int charge = this.getUseDuration(stack, entity) - timeLeft;

        // 最少蓄力
        if (charge < 5) return false;

        // 蓄力比例
        float power = Math.min(charge / 20f, 3f);

        // 距离随蓄力增加
        int distance = (int)(10 + power * 10); // 10~40格

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();

        // 粒子轨迹
        for (int i = 1; i < distance; i++) {

            Vec3 pos = start.add(direction.scale(i));

            serverLevel.sendParticles(
                    ParticleTypes.SONIC_BOOM,
                    pos.x,
                    pos.y,
                    pos.z,
                    1,
                    0,0,0,0
            );
        }

        // 命中检测
        AABB box = player.getBoundingBox()
                .expandTowards(direction.scale(distance))
                .inflate(2);

        List<Entity> entities = level.getEntities(player, box);

        for (Entity e : entities) {

            if (e instanceof LivingEntity living) {

                Vec3 toTarget = living.getEyePosition().subtract(start);

                if (toTarget.normalize().dot(direction) > 0.9) {

                    // 造成10点伤害
                    living.hurt(level.damageSources().sonicBoom(player), 10F);

                    living.push(direction.x * 2.5, direction.y * 0.5, direction.z * 2.5);

                    if (living instanceof Warden && player instanceof ServerPlayer serverPlayer) {
                        giveAdvancement(serverPlayer);
                    }
                }
            }
        }

        // 音效
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM,
                player.getSoundSource(),
                1F,
                1F
        );

        // 1秒冷却
        player.getCooldowns().addCooldown(stack, 20);

        // 消耗物品
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return super.releaseUsing(stack, level, entity, timeLeft);
    }

    private void giveAdvancement(ServerPlayer serverPlayer) {
        MinecraftServer server = serverPlayer.level().getServer();
        AdvancementHolder adv = server.getAdvancements().get(ResourceKey.create(Registries.ADVANCEMENT, Hasoook.id("echo_for_echo")).identifier());

        if (adv == null) return;

        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(adv);

        if (progress.isDone()) return;

        for (String criterion : progress.getRemainingCriteria()) {
            serverPlayer.getAdvancements().award(adv, criterion);
        }
    }

    // 附魔光效
    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return true;
    }

    // 最大使用时间
    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return 72000;
    }

    // 使用动画
    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
}
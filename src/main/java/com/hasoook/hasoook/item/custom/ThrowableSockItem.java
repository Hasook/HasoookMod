package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.custom.ThrownSockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * 单只袜子 — 继承 SocksItem 的臭味系统，额外可长按右键蓄力扔出。
 */
public class ThrowableSockItem extends SocksItem {

    public ThrowableSockItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return 72000;
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public @NonNull InteractionResult use(Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /**
     * 松开右键时触发 — 蓄力越久扔得越远
     */
    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level,
                                @NonNull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;

        int useTime = this.getUseDuration(stack, entity) - timeLeft;
        if (useTime < 5) return false;

        // 蓄力 1 秒满力（20 tick），速度范围 0.2 ~ 0.8
        float power = Math.min(useTime / 20f, 1.0f);
        float speed = 0.2f + power * 0.6f;

        if (!level.isClientSide()) {
            ThrownSockEntity projectile = new ThrownSockEntity(player, level);
            projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            var look = player.getViewVector(1.0f);
            projectile.shoot(look.x, look.y, look.z, speed, 1.0f);

            // 传递臭味等级
            int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
            projectile.setWear(wear);

            level.addFreshEntity(projectile);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                    0.5f, 0.4f / (level.random.nextFloat() * 0.4f + 0.8f));

            stack.consume(1, player);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return false;
    }

    /**
     * 近战攻击：消耗手里袜子，糊到对方脸上。
     */
    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target,
                          @NonNull LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
        int wearStage = SocksItem.getStage(wear);
        int texIndex = player.getRandom().nextInt(3);
        int seed = player.getRandom().nextInt(16);
        int packed = (texIndex << 24) | (wearStage << 16) | (seed << 12) | 2047;

        String cur = target.getData(ModAttachments.SOCK_FACE.get());
        String next = cur.isEmpty() ? String.valueOf(packed) : cur + "," + packed;
        target.setData(ModAttachments.SOCK_FACE.get(), next);

        stack.consume(1, player);
        super.hurtEnemy(stack, target, attacker);
    }
}

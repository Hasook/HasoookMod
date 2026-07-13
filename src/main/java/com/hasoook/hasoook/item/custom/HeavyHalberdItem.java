package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.entity.custom.HeavyHalberdProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public class HeavyHalberdItem extends Item {
    public HeavyHalberdItem(Properties properties) {
        super(properties.rarity(Rarity.RARE).stacksTo(1));
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity entity, int timeLeft) {
        if (level instanceof ServerLevel serverLevel) {
            // 判定蓄力时间，类似三叉戟，按住超过 10 ticks (0.5秒) 才能掷出
            int duration = this.getUseDuration(stack, entity) - timeLeft;
            if (duration >= 10) {
                Projectile.spawnProjectileFromRotation(
                        (lvl, shooter, item) -> {
                            HeavyHalberdProjectile projectile = new HeavyHalberdProjectile(shooter, lvl, item);
                            projectile.setBaseDamage(8.0); // 设置基础投掷伤害
                            return projectile;
                        },
                        serverLevel,
                        stack,
                        entity,
                        0.0F,
                        2.5F, // 投掷速度，三叉戟通常是 2.5F
                        1.0F  // 偏移/散布值
                );

                // 消耗物品 (非创造模式)
                if (entity instanceof Player player && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return super.releaseUsing(stack, level, entity, timeLeft);
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.nextDamageWillBreak()) {
            return InteractionResult.FAIL;
        } else if (EnchantmentHelper.getTridentSpinAttackStrength(itemstack, player) > 0.0F && !player.isInWaterOrRain()) {
            return InteractionResult.FAIL;
        } else {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack itemStack) {
        return ItemUseAnimation.TRIDENT; // 使用三叉戟的举起动作
    }

    @Override
    public int getUseDuration(@NonNull ItemStack itemStack, @NonNull LivingEntity livingEntity) {
        return 72000;
    }
}
package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static net.minecraft.world.item.component.KineticWeapon.getMotion;

public class FireworkRocketSpearItem extends Item {
    private static final float MAX_PUSH_STRENGTH = 0.08F; // 最大推进力度
    private static final int DURABILITY_CONSUME_INTERVAL = 20;
    private static final ResourceKey<Advancement> FIREWORK_IN_HAND = ResourceKey.create(Registries.ADVANCEMENT, Hasoook.id("not_just_another_firework"));

    public FireworkRocketSpearItem(Properties properties) {
        super(properties
                .spear(ToolMaterial.WOOD,  // 工具材料
                0.625F,  // 攻击速度 =4+(1/攻击速度系数−4)=1/攻击速度系数
                0.15F,  // 动能伤害倍率
                0.6F,   // 最小蓄力时间，玩家至少需要按住这么久才能生效
                2.5F,   // 第一个速度条件阈值（格/秒）
                8.0F,   // 第一个速度条件的奖励值
                6.75F,  // 第二个速度条件阈值（格/秒）
                5.1F,   // 第二个速度条件的奖励值
                11.25F, // 相对速度条件阈值（格/秒）
                4.6F)     // 相对速度条件的奖励值));
                .durability(9)
                .repairable(Items.GUNPOWDER));
    }

    // 当玩家开始使用时触发
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.SPEAR_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void inventoryTick(@NonNull ItemStack itemStack, @NonNull ServerLevel level, @NonNull Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        // 检查玩家是否正在使用此物品
        if (!livingEntity.isUsingItem() || livingEntity.getUseItem() != itemStack) {
            return;
        }

        int useTick = getUseDuration(itemStack, livingEntity) - livingEntity.getUseItemRemainingTicks(); //使用时间
        int maxActive = getMaxActiveTicks(itemStack); // 最大使用时间
        int kinetic = Objects.requireNonNull(getKinetic(itemStack)).delayTicks(); // 蓄力时间

        if (useTick >= kinetic && useTick <= maxActive) {
            if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
                // 检查玩家是否正在使用此物品
                if (livingEntity.isUsingItem() && livingEntity.getUseItem() == itemStack) {
                    // 获取实体的朝向
                    Vec3 lookAngle = livingEntity.getLookAngle().normalize();
                    // 获取向量
                    Vec3 upVector = new Vec3(0, 1, 0);
                    // 计算右向量（使用叉积：前向量 × 上向量 = 右向量）
                    Vec3 rightVector = lookAngle.cross(upVector).normalize();

                    // 根据手的不同调整偏移方向
                    double offsetFactor = (slot == EquipmentSlot.MAINHAND) ? 0.3 : -0.3; // 主手右，副手左

                    // 计算粒子位置：实体位置 + 向前偏移 + 左右偏移
                    Vec3 particlePos = entity.position()
                            .add(0, entity.getEyeHeight() * 0.8, 0) // 稍微低于眼睛高度
                            .add(lookAngle.scale(0.5)) // 向前偏移
                            .add(rightVector.scale(offsetFactor)); // 左右偏移

                    // 生成粒子
                    level.sendParticles(ParticleTypes.FIREWORK,
                            particlePos.x(), particlePos.y(), particlePos.z(),
                            1, 0.0D, 0.0D, 0.0D, 0.05D);
                }

                // 扣耐久
                if (livingEntity.tickCount % DURABILITY_CONSUME_INTERVAL == 0) {
                    int currentDamage = itemStack.getDamageValue();
                    int maxDamage = itemStack.getMaxDamage();

                    // 如果没耐久了，就直接爆炸
                    if (currentDamage >= maxDamage - 1) {
                        FireworkRocketEntity rocket = new FireworkRocketEntity(
                                level,
                                createFireworkByDurability(itemStack),
                                livingEntity,
                                livingEntity.getX(),
                                livingEntity.getEyeY(),
                                livingEntity.getZ(),
                                true
                        );
                        rocket.setDeltaMovement(Vec3.ZERO);
                        level.addFreshEntity(rocket);
                        rocket.explode(level);  // 立刻爆炸

                        // 授予进度
                        if (livingEntity instanceof ServerPlayer player) {
                            ItemStack firework = createFireworkByDurability(itemStack);

                            Fireworks fireworks = firework.get(DataComponents.FIREWORKS);

                            if (fireworks != null && !fireworks.explosions().isEmpty()) {
                                grantFireworkInHand(player);
                            }
                        }

                        livingEntity.stopUsingItem(); // 停止使用物品

                        return;
                    }

                    // 扣除耐久
                    itemStack.hurtAndBreak(1, livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());
                }
            }
        }
        super.inventoryTick(itemStack, level, entity, slot);
    }

    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity livingEntity, @NonNull ItemStack stack, int remainingUseDuration) {
        int useTick = getUseDuration(stack, livingEntity) - livingEntity.getUseItemRemainingTicks();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        if (useTick == kinetic) {
            level.playSound(livingEntity, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (useTick >= maxActive || useTick <= kinetic) {
            return;
        }

        Entity target = livingEntity.getVehicle() != null ? livingEntity.getVehicle() : livingEntity;

        Vec3 forward = livingEntity.getLookAngle().normalize().scale(MAX_PUSH_STRENGTH);

        // 推进
        target.addDeltaMovement(forward);

        // 限速
        Vec3 motion = target.getDeltaMovement();
        if (motion.length() > 3.0D) {
            target.setDeltaMovement(motion.normalize().scale(3.0D));
        }

        super.onUseTick(level, livingEntity, stack, remainingUseDuration);
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level, @NonNull LivingEntity livingEntity, int timeLeft) {
        if (level.isClientSide()) return false;

        if (timeLeft < 4) return true;

        int useTick = getUseDuration(stack, livingEntity) - livingEntity.getUseItemRemainingTicks();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        if (useTick >= maxActive || useTick <= kinetic) {
            return true; // 如果大于使用时间或没蓄力就跳过
        }

        Vec3 look = livingEntity.getLookAngle();

        // 生成烟花火箭
        FireworkRocketEntity rocket = new FireworkRocketEntity(
                level,
                createFireworkByDurability(stack),
                livingEntity,
                livingEntity.getX(),
                livingEntity.getEyeY() - 0.15,
                livingEntity.getZ(),
                true
        );

        // 让火箭朝玩家视线方向飞
        rocket.setDeltaMovement(
                look.x * 0.8,
                look.y * 0.8,
                look.z * 0.8
        );

        level.addFreshEntity(rocket);

        stack.hurtAndBreak(stack.getMaxDamage(), livingEntity, InteractionHand.MAIN_HAND.asEquipmentSlot());

        super.releaseUsing(stack, level, livingEntity, timeLeft);
        return true;
    }

    @Override
    public void onStopUsing(@NonNull ItemStack stack, @NonNull LivingEntity livingEntity, int timeLeft) {
        if (livingEntity.level().isClientSide()) return;

        // 停止使用直接销毁
        stack.hurtAndBreak(stack.getMaxDamage(), livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());

        super.onStopUsing(stack, livingEntity, timeLeft);
    }

    private static ItemStack createFireworkByDurability(ItemStack spearStack) {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);

        // 获取矛上的烟花组件
        Fireworks original = spearStack.get(DataComponents.FIREWORKS);

        // 计算飞行时间
        int flight = (spearStack.getMaxDamage() - spearStack.getDamageValue()) / 4;

        Fireworks newFireworks;
        if (original != null) {
            // 复制原来的物品组件，但替换飞行时间
            newFireworks = new Fireworks(flight, original.explosions());
        } else {
            // 没有原始组件就创建空的
            newFireworks = new Fireworks(flight, List.of());
        }

        firework.set(DataComponents.FIREWORKS, newFireworks);
        return firework;
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide()) {
            int useTick = getUseDuration(stack, attacker) - attacker.getUseItemRemainingTicks();
            int maxActive = getMaxActiveTicks(stack);
            int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

            // 如果超过最大使用时间或者没有蓄力完成则跳过
            if (useTick >= maxActive || useTick <= kinetic) {
                return;
            }

            // 生成烟花火箭
            FireworkRocketEntity rocket = new FireworkRocketEntity(
                    level,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    createFireworkByDurability(stack)
            );

            rocket.setDeltaMovement(0.0D, 0.3D, 0.0D); // 给向上的初速度

            rocket.setPos(target.getX(), target.getY() + 0.1D, target.getZ()); // 设置位置
            level.addFreshEntity(rocket);
            target.startRiding(rocket); // 让目标骑上火箭
            stack.hurtAndBreak(stack.getMaxDamage(), attacker, attacker.getUsedItemHand().asEquipmentSlot());
        }
        super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public boolean onDroppedByPlayer(@NonNull ItemStack stack, @NonNull Player player) {
        // 客户端直接返回
        if (player.level().isClientSide()) {
            return false;
        }

        Level level = player.level();
        int useTick = getUseDuration(stack, player) - player.getUseItemRemainingTicks();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        // 如果没使用，就直接丢出。否则销毁物品并且生成烟花火箭
        if (useTick >= maxActive || useTick <= kinetic - 8) {
            return true;
        }

        Vec3 look = player.getLookAngle();

        // 生成烟花火箭
        FireworkRocketEntity rocket = new FireworkRocketEntity(
                level,
                createFireworkByDurability(stack),
                player,
                player.getX(),
                player.getEyeY() - 0.15,
                player.getZ(),
                true
        );
        rocket.setDeltaMovement(look.x * 0.8, look.y * 0.8, look.z * 0.8);
        level.addFreshEntity(rocket);

        // 创造模式玩家直接删除物品
        if (player.isCreative()) {
            player.getInventory().removeItem(stack);
            return false; // 阻止物品掉落
        }

        stack.hurtAndBreak(stack.getMaxDamage(), player, player.getUsedItemHand().asEquipmentSlot()); // 销毁物品

        return true; // 阻止物品掉落
    }

    private static void grantFireworkInHand(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        AdvancementHolder adv = server.getAdvancements().get(FIREWORK_IN_HAND.identifier());

        if (adv == null) return;

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);

        if (progress.isDone()) return;

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    @Override
    public int getUseDuration(@NonNull ItemStack itemStack, @NonNull LivingEntity livingEntity) {
        return 72000; // 设置长按的时间
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack itemStack) {
        return ItemUseAnimation.SPEAR; // 使用矛的动画
    }

    // 获取蓄力时间
    @Nullable
    private static KineticWeapon getKinetic(ItemStack stack) {
        return stack.get(DataComponents.KINETIC_WEAPON);
    }

    // 计算最大使用时间
    private static int getMaxActiveTicks(ItemStack stack) {
        KineticWeapon kinetic = getKinetic(stack);
        if (kinetic == null) return Integer.MAX_VALUE;

        return kinetic.computeDamageUseDuration();
    }
}
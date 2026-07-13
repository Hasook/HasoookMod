package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.consume.UseAction;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class FireworkRocketSpearItem extends Item {
    private static final int DURABILITY_CONSUME_INTERVAL = 20; // 每20tick(1秒)消耗1点耐久
    private static final float MAX_PUSH_STRENGTH = 0.08F; // 烟花推进最大力度

    public FireworkRocketSpearItem(Settings settings) {
        super(settings
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
                .maxDamage(9)
                .repairable(Items.GUNPOWDER));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        // 检查玩家是否正在使用此物品
        if (!livingEntity.isUsingItem() || livingEntity.getActiveItem() != stack) {
            return;
        }

        int useTick = getMaxUseTime(stack, livingEntity) - livingEntity.getItemUseTimeLeft(); // 使用时间
        int maxActive = getMaxActiveTicks(stack); // 最大使用时间
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks(); // 蓄力时间

        if (useTick >= kinetic && useTick <= maxActive) {
            boolean isMainHand = livingEntity.getMainHandStack() == stack;
            if (isMainHand || livingEntity.getOffHandStack() == stack) {
                // 检查玩家是否正在使用此物品
                if (livingEntity.isUsingItem() && livingEntity.getActiveItem() == stack) {
                    // 获取实体的朝向
                    Vec3d lookAngle = livingEntity.getRotationVector().normalize();
                    // 获取向量
                    Vec3d upVector = new Vec3d(0, 1, 0);
                    // 计算右向量（使用叉积：前向量 × 上向量 = 右向量）
                    Vec3d rightVector = lookAngle.crossProduct(upVector).normalize();

                    // 根据手的不同调整偏移方向
                    double offsetFactor = isMainHand ? 0.3 : -0.3; // 主手右，副手左

                    // 计算粒子位置：实体位置 + 向前偏移 + 左右偏移
                    Vec3d particlePos = entity.getEntityPos()
                            .add(0, entity.getStandingEyeHeight() * 0.8, 0) // 稍微低于眼睛高度
                            .add(lookAngle.multiply(0.5)) // 向前偏移
                            .add(rightVector.multiply(offsetFactor)); // 左右偏移

                    // 生成粒子
                    world.spawnParticles(ParticleTypes.FIREWORK,
                            particlePos.x, particlePos.y, particlePos.z,
                            1, 0.0D, 0.0D, 0.0D, 0.05D);
                }

                // 扣耐久
                if (livingEntity.age % DURABILITY_CONSUME_INTERVAL == 0) {
                    int currentDamage = stack.getDamage();
                    int maxDamage = stack.getMaxDamage();

                    // 如果没耐久了，就直接爆炸
                    if (currentDamage >= maxDamage - 1) {
                        FireworkRocketEntity rocket = new FireworkRocketEntity(
                                world,
                                createFireworkByDurability(stack),
                                livingEntity,
                                livingEntity.getX(),
                                livingEntity.getEyeY(),
                                livingEntity.getZ(),
                                true
                        );
                        rocket.setVelocity(Vec3d.ZERO);
                        world.spawnEntity(rocket);

                        // 授予进度
                        if (livingEntity instanceof ServerPlayerEntity player) {
                            ItemStack firework = createFireworkByDurability(stack);

                            FireworksComponent fireworks = firework.get(DataComponentTypes.FIREWORKS);

                            if (fireworks != null && !fireworks.explosions().isEmpty()) {
                                grantFireworkInHand(player);
                            }
                        }

                        livingEntity.clearActiveItem(); // 停止使用物品

                        return;
                    }

                    // 扣除耐久
                    EquipmentSlot equipmentSlot = isMainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                    stack.damage(1, livingEntity, equipmentSlot);
                }
            }
        }
        super.inventoryTick(stack, world, entity, slot);
    }

    @Override
    public void usageTick(World world, LivingEntity livingEntity, ItemStack stack, int remainingUseTicks) {
        int useTick = getMaxUseTime(stack, livingEntity) - livingEntity.getItemUseTimeLeft();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        if (useTick == kinetic) {
            world.playSound(livingEntity, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        if (useTick >= maxActive || useTick <= kinetic) {
            return;
        }

        Entity target = livingEntity.getVehicle() != null ? livingEntity.getVehicle() : livingEntity;

        Vec3d forward = livingEntity.getRotationVector().normalize().multiply(MAX_PUSH_STRENGTH);

        // 推进
        target.addVelocity(forward);

        // 限速
        Vec3d motion = target.getVelocity();
        if (motion.length() > 3.0D) {
            target.setVelocity(motion.normalize().multiply(3.0D));
        }

        super.usageTick(world, livingEntity, stack, remainingUseTicks);
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity livingEntity, int remainingUseTicks) {
        if (world.isClient()) return false;

        if (remainingUseTicks < 4) return true;

        int useTick = getMaxUseTime(stack, livingEntity) - livingEntity.getItemUseTimeLeft();
        int maxActive = getMaxActiveTicks(stack);
        int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

        if (useTick >= maxActive || useTick <= kinetic) {
            return true; // 如果大于使用时间或没蓄力就跳过
        }

        Vec3d look = livingEntity.getRotationVector();

        // 生成烟花火箭
        FireworkRocketEntity rocket = new FireworkRocketEntity(
                world,
                createFireworkByDurability(stack),
                livingEntity,
                livingEntity.getX(),
                livingEntity.getEyeY() - 0.15,
                livingEntity.getZ(),
                true
        );

        // 让火箭朝玩家视线方向飞
        rocket.setVelocity(
                look.x * 0.8,
                look.y * 0.8,
                look.z * 0.8
        );

        world.spawnEntity(rocket);

        stack.damage(stack.getMaxDamage(), livingEntity, EquipmentSlot.MAINHAND);

        return true;
    }

    // 当实体被攻击时触发（NeoForge的hurtEnemy → Yarn的postHit）
    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        World world = attacker.getEntityWorld();
        if (!world.isClient()) {
            int useTick = getMaxUseTime(stack, attacker) - attacker.getItemUseTimeLeft();
            int maxActive = getMaxActiveTicks(stack);
            int kinetic = Objects.requireNonNull(getKinetic(stack)).delayTicks();

            // 如果超过最大使用时间或者没有蓄力完成则跳过
            if (useTick >= maxActive || useTick <= kinetic) {
                return;
            }

            // 生成烟花火箭
            FireworkRocketEntity rocket = new FireworkRocketEntity(
                    world,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    createFireworkByDurability(stack)
            );

            rocket.setVelocity(0.0D, 0.3D, 0.0D); // 给向上的初速度

            rocket.setPos(target.getX(), target.getY() + 0.1D, target.getZ()); // 设置位置
            world.spawnEntity(rocket);
            target.startRiding(rocket); // 让目标骑上火箭
            stack.damage(stack.getMaxDamage(), attacker,
                    attacker.getActiveHand() == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        super.postHit(stack, target, attacker);
    }

    // TODO: 原始NeoForge代码中的onDroppedByPlayer在Fabric/Yarn中不存在。
    // 需要通过Mixin到PlayerEntity.dropItem或使用Fabric事件来实现物品丢弃时生成烟花火箭的功能。
    // 原始行为：如果玩家在蓄力后丢弃该物品，会生成一个烟花火箭而不是让物品掉落。

    private static ItemStack createFireworkByDurability(ItemStack spearStack) {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);

        // 获取矛上的烟花组件
        FireworksComponent original = spearStack.get(DataComponentTypes.FIREWORKS);

        // 计算飞行时间
        int flight = (spearStack.getMaxDamage() - spearStack.getDamage()) / 4;

        FireworksComponent newFireworks;
        if (original != null) {
            // 复制原来的物品组件，但替换飞行时间
            newFireworks = new FireworksComponent(flight, original.explosions());
        } else {
            // 没有原始组件就创建空的
            newFireworks = new FireworksComponent(flight, List.of());
        }

        firework.set(DataComponentTypes.FIREWORKS, newFireworks);
        return firework;
    }

    private static void grantFireworkInHand(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();

        AdvancementEntry adv = server.getAdvancementLoader().get(Hasoook.id("not_just_another_firework"));

        if (adv == null) return;

        PlayerAdvancementTracker tracker = player.getAdvancementTracker();
        AdvancementProgress progress = tracker.getProgress(adv);

        if (progress.isDone()) return;

        for (String criterion : progress.getUnobtainedCriteria()) {
            tracker.grantCriterion(adv, criterion);
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000; // 设置长按的时间
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR; // 使用矛的动画
    }

    // 获取蓄力时间
    @Nullable
    private static KineticWeaponComponent getKinetic(ItemStack stack) {
        return stack.get(DataComponentTypes.KINETIC_WEAPON);
    }

    // 计算最大使用时间
    private static int getMaxActiveTicks(ItemStack stack) {
        KineticWeaponComponent kinetic = getKinetic(stack);
        if (kinetic == null) return Integer.MAX_VALUE;

        return kinetic.getUseTicks();
    }
}

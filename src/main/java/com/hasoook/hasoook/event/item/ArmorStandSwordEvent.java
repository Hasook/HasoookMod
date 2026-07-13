package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.custom.ArmorStandSwordProjectile;
import com.hasoook.hasoook.item.custom.ArmorStandSwordItem;
import com.hasoook.hasoook.item.custom.MobHeadItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.*;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class ArmorStandSwordEvent {

    private static final int SLOT_COUNT = 4;
    // 防止同一 tick 多次触发（横扫攻击等场景）
    private static final Map<UUID, Integer> LAST_TRIGGER_TICK = new HashMap<>();

    @SubscribeEvent
    public static void onPostDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Level level = target.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 先检查直接来源（弹射物自身），再检查根本来源（攻击者）
        // 原因：DamageSource.getEntity() 对投掷物返回的是投掷者（玩家），
        // 只有 getDirectEntity() 才返回弹射物本身。
        // 如果先判断 getEntity()，弹射物命中时会错误地走入手持攻击分支，
        // 使用玩家手上当前的剑而非弹射物携带的剑。
        Entity directEntity = event.getSource().getDirectEntity();
        Entity causingEntity = event.getSource().getEntity();

        LivingEntity attacker;
        ItemStack swordStack;

        // 情况1：投掷攻击 → 从弹射物取剑的数据（必须最先判断！）
        if (directEntity instanceof ArmorStandSwordProjectile projectile) {
            Entity owner = projectile.getOwner();
            if (!(owner instanceof LivingEntity livingOwner)) return;
            attacker = livingOwner;
            swordStack = projectile.getItem();
            if (!(swordStack.getItem() instanceof ArmorStandSwordItem)) return;
        }
        // 情况2：手持攻击 → 攻击者即生物实体本身，从主手取剑
        else if (causingEntity instanceof LivingEntity livingAttacker) {
            attacker = livingAttacker;
            swordStack = attacker.getMainHandItem();
            if (!(swordStack.getItem() instanceof ArmorStandSwordItem)) return;
        }
        else {
            return;
        }

        // 同一 tick 去重
        int currentTick = serverLevel.getServer().getTickCount();
        UUID attackerId = attacker.getUUID();
        Integer lastTick = LAST_TRIGGER_TICK.get(attackerId);
        if (lastTick != null && lastTick == currentTick) {
            return;
        }
        LAST_TRIGGER_TICK.put(attackerId, currentTick);

        // 获取最终造成的伤害（已包含暴击等加成）
        float finalDamage = event.getNewDamage();

        // 触发生物头颅特殊效果
        handleMobHeadEffects(swordStack, target, attacker, finalDamage);
    }

    // ═══════════════════════════════════════════════════════════════
    // 数据读取（与 ArmorStandSwordItem 保持一致）
    // ═══════════════════════════════════════════════════════════════

    private static NonNullList<ItemStack> readItems(ItemStack stack) {
        ItemContainerContents icc = stack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(), ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        List<ItemStack> stored = icc.stream().toList();
        for (int i = 0; i < SLOT_COUNT && i < stored.size(); i++) {
            ItemStack s = stored.get(i);
            if (s != null && !s.isEmpty()) items.set(i, s.copy());
        }
        return items;
    }

    // ═══════════════════════════════════════════════════════════════
    // 生物头颅特殊效果
    // ═══════════════════════════════════════════════════════════════

    /**
     * 根据剑上装备的头颅类型触发不同的攻击特效。
     * 支持原版头颅物品和自定义生物头物品。
     *
     * @param finalDamage 最终造成的伤害（已包含暴击等加成），用于猪灵爆金币计算
     */
    private static void handleMobHeadEffects(ItemStack swordStack, LivingEntity target,
                                              LivingEntity attacker, float finalDamage) {
        NonNullList<ItemStack> items = readItems(swordStack);
        ItemStack headStack = items.get(0); // slot 0 = 头盔槽位
        if (headStack.isEmpty()) return;

        Item headItem = headStack.getItem();
        RandomSource random = attacker.level().getRandom();

        if (headItem == Items.SKELETON_SKULL) {
            applyBoneMealEffect(target, (ServerLevel) attacker.level(), finalDamage);
        } else if (headItem == Items.CREEPER_HEAD) {
            applyCreeperExplosion(target, attacker, finalDamage);
        } else if (headItem == Items.WITHER_SKELETON_SKULL) {
            applyWitherEffect(target);
        } else if (headItem == Items.PIGLIN_HEAD) {
            spawnGoldDrop(target, finalDamage);
        } else if (headItem == Items.DRAGON_HEAD) {
            applyDragonBreath(target, attacker, finalDamage);
        } else if (headItem instanceof MobHeadItem) {
            handleCustomMobHead(headStack, target, attacker, finalDamage);
        }
    }

    /**
     * 处理自定义生物头（MobHeadItem）的效果，
     * 根据捕获的实体类型匹配对应特效。
     */
    private static void handleCustomMobHead(ItemStack headStack, LivingEntity target, LivingEntity attacker, float finalDamage) {
        EntityType<?> type = MobHeadItem.getEntityType(headStack);
        if (type == null) return;

        if (type == EntityType.SKELETON || type == EntityType.STRAY) {
            applyBoneMealEffect(target, (ServerLevel) attacker.level(), finalDamage);
        } else if (type == EntityType.CREEPER) {
            applyCreeperExplosion(target, attacker, finalDamage);
        } else if (type == EntityType.WITHER_SKELETON) {
            applyWitherEffect(target);
        } else if (type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) {
            spawnGoldDrop(target, finalDamage);
        } else if (type == EntityType.ENDER_DRAGON) {
            applyDragonBreath(target, attacker, finalDamage);
        }
    }

    /** 骷髅头颅：伤害越高，骨粉领域越大 */
    private static void applyBoneMealEffect(LivingEntity target, ServerLevel level, float finalDamage) {
        BlockPos center = target.blockPosition();

        // 伤害转成长半径
        int radius = Math.min(32, 3 + (int)(finalDamage / 5));

        // 催熟次数
        int attempts = radius * radius;

        // 粒子范围
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                target.getX(),
                target.getY()+1,
                target.getZ(),
                attempts,
                radius * 0.3,
                1,
                radius * 0.3,
                0.05
        );

        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);

        RandomSource random = level.random;

        for(int i = 0; i < attempts; i++){


            double angle = random.nextDouble() * Math.PI * 2;

            double distance =
                    random.nextDouble() * radius;


            int x =
                    center.getX()
                            +(int)(Math.cos(angle)*distance);


            int z =
                    center.getZ()
                            +(int)(Math.sin(angle)*distance);


            int y =
                    center.getY()
                            +random.nextInt(3)-1;


            BlockPos pos =
                    new BlockPos(x,y,z);


            BoneMealItem.applyBonemeal(
                    boneMeal,
                    level,
                    pos,
                    null
            );
        }

        level.playSound(
                null,
                center,
                SoundEvents.BONE_MEAL_USE,
                SoundSource.PLAYERS,
                1F,
                0.8F + random.nextFloat()*0.4F
        );
    }

    /** 苦力怕头颅：爆炸半径随伤害增大，但克制上限为 3.0 */
    private static void applyCreeperExplosion(LivingEntity target, LivingEntity attacker, float finalDamage) {
        ServerLevel level = (ServerLevel) target.level();

        // 半径克制增长，基础1.0，每点伤害+0.2，最大3.0
        float explosionRadius = Math.min(3.0F, 1.0F + finalDamage * 0.2F);

        level.explode(
                attacker,
                target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5,
                explosionRadius,
                Level.ExplosionInteraction.MOB
        );
    }

    /** 凋零骷髅头颅：给予凋零效果 */
    private static void applyWitherEffect(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1)); // 5秒 等级II
    }

    /**
     * 猪灵头颅：爆金币（掉落金锭或金粒）。
     * 伤害使用最终造成的伤害（已包含暴击加成）。
     */
    private static void spawnGoldDrop(LivingEntity target, float finalDamage) {
        ServerLevel level = (ServerLevel) target.level();
        RandomSource random = level.random;
        ItemStack gold;

        // 使用最终伤害（含暴击）来计算掉落数量
        if (random.nextFloat() < 0.4F) {
            // 40% 概率掉落金锭，数量 = 最终伤害/3（至少1个）
            int count = Math.max(1, Math.round(finalDamage / 3.0f));
            gold = new ItemStack(Items.GOLD_INGOT, count);
        } else {
            // 否则掉落金粒，数量 = 最终伤害（至少2个）+ 随机0~2
            int base = Math.max(2, Math.round(finalDamage));
            int extra = random.nextInt(3);
            gold = new ItemStack(Items.GOLD_NUGGET, base + extra);
        }

        ItemEntity itemEntity = new ItemEntity(level,
                target.getX(), target.getY() + 1.0, target.getZ(), gold);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);

        level.playSound(null, target.blockPosition(),
                SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.PLAYERS,
                0.8F, 0.8F + random.nextFloat() * 0.4F);
    }

    /** 末影龙头：生成滞留型龙息区域效果云，伤害越高范围越大，停留时间削弱 */
    private static void applyDragonBreath(LivingEntity target, LivingEntity attacker, float finalDamage) {
        ServerLevel level = (ServerLevel) target.level();
        RandomSource random = level.random;

        // 半径随伤害增大，基础1.5，每点伤害+0.5，最大6.0
        float radius = Math.min(6.0F, 1.5F + finalDamage * 0.5F);

        AreaEffectCloud cloud = new AreaEffectCloud(level,
                target.getX(), target.getY(), target.getZ());
        cloud.setOwner(attacker);
        cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
        cloud.setRadius(radius);
        cloud.setDuration(100);          // 停留时间削弱为 5 秒（原 300 = 15 秒）
        cloud.setWaitTime(10);
        cloud.setRadiusOnUse(0.0F);
        cloud.setRadiusPerTick(-0.005F); // 缓慢收缩
        cloud.setPotionDurationScale(0.33F);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0)); // 瞬间伤害

        level.addFreshEntity(cloud);
        level.playSound(null, target.blockPosition(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS,
                0.5F, 0.8F + random.nextFloat() * 0.4F);
    }
}

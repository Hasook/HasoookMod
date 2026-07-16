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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.*;
import java.util.function.Predicate;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class ArmorStandSwordEvent {

    private static final int SLOT_COUNT = 4;
    // 防止同一 tick 多次触发（横扫攻击等场景）
    private static final Map<UUID, Integer> LAST_TRIGGER_TICK = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // 重锤（削弱版）— 剑上装备 ≥4 件时触发
    // ═══════════════════════════════════════════════════════════════

    /** 伤害系数（原版重锤为 1.0，削弱版为 0.5） */
    private static final float SMASH_DAMAGE_FACTOR = 0.5F;
    /** 最低掉落距离（与原版一致） */
    private static final float SMASH_FALL_THRESHOLD = 1.5F;
    /** 重击判定阈值（与原版一致） */
    private static final float SMASH_HEAVY_THRESHOLD = 5.0F;
    /** 击退半径（原版 3.5，削弱版 3.0） */
    private static final double SMASH_KNOCKBACK_RADIUS = 3.0;
    /** 击退力度（原版 0.7，削弱版取 1/3 ≈ 0.23） */
    private static final float SMASH_KNOCKBACK_POWER = 0.23F;
    /** 记录重锤攻击，供 Post 事件和摔落保护使用 */
    private static final Map<UUID, SmashRecord> SMASH_RECORDS = new HashMap<>();

    private record SmashRecord(float fallDistance, int tick) {}

    /**
     * 判断是否满足重锤攻击条件（模仿原版 MaceItem.canSmashAttack）。
     * 摔落距离 > 1.5 且未使用鞘翅飞行。
     */
    private static boolean canSmashAttack(LivingEntity entity) {
        return entity.fallDistance > (double) SMASH_FALL_THRESHOLD && !entity.isFallFlying();
    }

    // ═══════════════════════════════════════════════════════════════
    // 重锤 Pre 事件：根据掉落距离增加伤害
    // ═══════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onPreDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        Entity directEntity = event.getSource().getDirectEntity();
        Entity causingEntity = event.getSource().getEntity();

        ItemStack swordStack;
        LivingEntity attacker;
        double velocityY; // 攻击实体的 Y 轴速度

        // 情况1：弹射物攻击 → 用弹射物的实时速度
        if (directEntity instanceof ArmorStandSwordProjectile projectile) {
            swordStack = projectile.getItem();
            if (!(swordStack.getItem() instanceof ArmorStandSwordItem)) return;
            velocityY = projectile.getDeltaMovement().y;
            attacker = projectile.getOwner() instanceof LivingEntity owner ? owner : null;
        }
        // 情况2：近战攻击 → 用攻击者的实时速度
        else if (causingEntity instanceof LivingEntity livingAttacker) {
            swordStack = livingAttacker.getMainHandItem();
            if (!(swordStack.getItem() instanceof ArmorStandSwordItem)) return;
            attacker = livingAttacker;
            velocityY = attacker.getDeltaMovement().y;
        }
        else {
            return;
        }

        int stored = countStored(swordStack);
        float armorVal = getTotalArmorValue(swordStack);
        if (stored < 4) {
            Hasoook.LOGGER.info("[Smash] 装备不足: {} < 4", stored);
            return;
        }
        if (armorVal <= 10.0F) {
            Hasoook.LOGGER.info("[Smash] 总护甲值不足: {} <= 10", armorVal);
            return;
        }

        // 用向下速度替代摔落距离：v²=2gh → h=v²/(2g), g=0.08
        float downwardSpeed = (float) Math.max(0, -velocityY);
        if (downwardSpeed < 0.5F) {
            Hasoook.LOGGER.info("[Smash] 向下速度不足: {} < 0.5 (vY={})",
                    downwardSpeed, velocityY);
            return;
        }
        float fallDistance = downwardSpeed * downwardSpeed / 0.16F; // 等效摔落距离

        // 原版重锤分段增伤公式（来自 MaceItem.getAttackDamageBonus）
        // d≤3: 4d  |  3<d≤8: 12+2(d-3)  |  d>8: 22+(d-8)
        // 削弱版乘以 SMASH_DAMAGE_FACTOR（默认 0.5）
        double vanillaBonus;
        if (fallDistance <= 3.0F) {
            vanillaBonus = 4.0F * fallDistance;
        } else if (fallDistance <= 8.0F) {
            vanillaBonus = 12.0F + 2.0F * (fallDistance - 3.0F);
        } else {
            vanillaBonus = 22.0F + (fallDistance - 8.0F);
        }

        float bonus = (float) (vanillaBonus * SMASH_DAMAGE_FACTOR);
        if (bonus <= 0) return;

        float newDamage = event.getNewDamage() + bonus;
        event.setNewDamage(newDamage);

        Hasoook.LOGGER.info("[Smash] 重锤触发! downwardSpeed={}, equivalentFall={}, bonus={}, total={}",
                downwardSpeed, fallDistance, bonus, newDamage);

        if (attacker != null) {
            SMASH_RECORDS.put(attacker.getUUID(),
                    new SmashRecord(fallDistance, serverLevel.getServer().getTickCount()));
        }
    }

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

        // 防止递归触发：AreaEffectCloud（如龙息云）造成的伤害不应再触发头颅效果，
        // 否则龙息 → 伤害 → 再次龙息 → 再次伤害 → 无限循环
        if (directEntity instanceof AreaEffectCloud) {
            return;
        }

        // 获取最终造成的伤害（已包含暴击等加成）
        float finalDamage = event.getNewDamage();

        // 触发生物头颅特殊效果
        handleMobHeadEffects(swordStack, target, attacker, finalDamage);

        // 触发重锤攻击效果（近战和投掷均可触发）
        handleSmashAttack(target, attacker);
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

    private static int countStored(ItemStack stack) {
        int c = 0;
        for (ItemStack s : readItems(stack)) if (!s.isEmpty()) c++;
        return c;
    }

    /** 计算剑上装备的总护甲值（护甲 + 韧性） */
    private static float getTotalArmorValue(ItemStack stack) {
        float total = 0;
        for (ItemStack stored : readItems(stack)) {
            if (stored.isEmpty()) continue;
            ItemAttributeModifiers mods = stored.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (mods == null) {
                mods = stored.getItem().components()
                        .get(DataComponents.ATTRIBUTE_MODIFIERS);
            }
            if (mods != null) {
                for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                    if (entry.attribute().equals(Attributes.ARMOR)
                            || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
                        total += (float) entry.modifier().amount();
                    }
                }
            }
        }
        return total;
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
        } else if (headItem == Items.ZOMBIE_HEAD) {
            applyZombieExperience(target, attacker, finalDamage);
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
        } else if (type == EntityType.ZOMBIE || type == EntityType.HUSK
                || type == EntityType.DROWNED || type == EntityType.ZOMBIE_VILLAGER) {
            applyZombieExperience(target, attacker, finalDamage);
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

    /** 僵尸头颅：伤害越高，生成的经验球越多 */
    private static void applyZombieExperience(LivingEntity target, LivingEntity attacker, float finalDamage) {
        ServerLevel level = (ServerLevel) target.level();
        RandomSource random = level.random;

        // 60% 概率生成经验球
        if (random.nextFloat() > 0.6F) {
            return;
        }

        // 经验值 = 伤害 × 3，最少 1 点，最多 50 点
        int xpValue = Math.max(1, Math.min(50, Math.round(finalDamage * 3.0f)));

        net.minecraft.world.entity.ExperienceOrb xpOrb =
                new net.minecraft.world.entity.ExperienceOrb(
                        level,
                        target.getX(),
                        target.getY() + 1.0,
                        target.getZ(),
                        xpValue
                );
        level.addFreshEntity(xpOrb);

        level.playSound(null, target.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.5F, 0.8F + random.nextFloat() * 0.4F);
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

    // ═══════════════════════════════════════════════════════════════
    // 重锤攻击效果（削弱版，模仿 MaceItem.hurtEnemy + postHurtEnemy）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 处理重锤攻击的粒子、音效、击退与摔落保护。
     * 模仿原版 MaceItem 的 hurtEnemy 和 postHurtEnemy 流程。
     */
    private static void handleSmashAttack(LivingEntity target, LivingEntity attacker) {
        SmashRecord record = SMASH_RECORDS.remove(attacker.getUUID());
        if (record == null) {
            Hasoook.LOGGER.info("[Smash] Post事件中未找到重锤记录 for UUID={}", attacker.getUUID());
            return;
        }

        float fallDist = record.fallDistance();
        Hasoook.LOGGER.info("[Smash] 执行重锤特效: fallDist={}", fallDist);
        ServerLevel level = (ServerLevel) target.level();

        // ── 设置攻击者 Y 轴速度（模仿原版重锤）──
        attacker.setDeltaMovement(
                attacker.getDeltaMovement().x,
                0.01,
                attacker.getDeltaMovement().z);

        // ── 粒子：使用原版的 levelEvent 2013（阵风冲击波）──
        level.levelEvent(2013, target.getOnPos(), 500);

        // ── 音效：在目标（受击点）位置播放 ──
        if (target.onGround()) {
            SoundEvent sound = fallDist > SMASH_HEAVY_THRESHOLD
                    ? SoundEvents.MACE_SMASH_GROUND_HEAVY
                    : SoundEvents.MACE_SMASH_GROUND;
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    sound, target.getSoundSource(), 0.8F, 1.0F);
        } else {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.MACE_SMASH_AIR, target.getSoundSource(), 0.8F, 1.0F);
        }

        // ── 击退（模仿原版 MaceItem.knockback）──
        knockback(level, attacker, target, fallDist);

        // ── 重置摔落距离（模仿原版 postHurtEnemy）──
        attacker.resetFallDistance();
    }

    /**
     * 击退范围内生物（模仿原版 MaceItem.knockback）。
     * 范围比原版小（3.0 vs 3.5），力度比原版弱（0.5 vs 0.7）。
     */
    private static void knockback(ServerLevel level, Entity attacker, Entity target, float fallDistance) {
        level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(SMASH_KNOCKBACK_RADIUS),
                        knockbackPredicate(attacker, target))
                .forEach(e -> {
                    Vec3 offset = e.position().subtract(target.position());
                    double power = getKnockbackPower(fallDistance, e, offset);
                    if (power > 0.0) {
                        Vec3 velocity = offset.normalize().scale(power);
                        e.push(velocity.x, 0.7, velocity.z);
                    }
                });
    }

    /**
     * 击退判定条件（模仿原版 MaceItem.knockbackPredicate）。
     */
    private static Predicate<LivingEntity> knockbackPredicate(Entity attacker, Entity target) {
        return e -> !e.isSpectator()
                && e != attacker && e != target
                && !attacker.isAlliedTo(e)
                && !(e instanceof TamableAnimal tamable && tamable.isTame()
                        && target instanceof LivingEntity livingTarget && tamable.isOwnedBy(livingTarget))
                && !(e instanceof ArmorStand armorStand && armorStand.isMarker())
                && target.distanceToSqr(e) <= SMASH_KNOCKBACK_RADIUS * SMASH_KNOCKBACK_RADIUS
                && !(e instanceof Player player && player.isCreative() && player.getAbilities().flying);
    }

    /**
     * 击退力度计算（模仿原版 MaceItem.getKnockbackPower）。
     * 力度与距离成反比，重击翻倍，考虑击退抗性。
     */
    private static double getKnockbackPower(float fallDistance, LivingEntity entity, Vec3 offset) {
        return (SMASH_KNOCKBACK_RADIUS - offset.length())
                * SMASH_KNOCKBACK_POWER
                * (fallDistance > SMASH_HEAVY_THRESHOLD ? 2.0 : 1.0)
                * (1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    // ═══════════════════════════════════════════════════════════════
    // 摔落保护：重锤攻击后短时间内免疫摔落伤害
    // ═══════════════════════════════════════════════════════════════

    private static final int SMASH_PROTECTION_TICKS = 10;

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        SmashRecord record = SMASH_RECORDS.get(entity.getUUID());
        if (record == null) return;

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        int currentTick = serverLevel.getServer().getTickCount();
        if (currentTick - record.tick() <= SMASH_PROTECTION_TICKS) {
            event.setDamageMultiplier(0.0F);
        }
        SMASH_RECORDS.remove(entity.getUUID());
    }
}

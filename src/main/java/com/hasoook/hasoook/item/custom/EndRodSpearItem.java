package com.hasoook.hasoook.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class EndRodSpearItem extends Item {
    public EndRodSpearItem(Properties properties) {
        super(properties
                .spear(ToolMaterial.IRON, 0.85F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
                .durability(64)
                .repairable(Items.END_ROD)
                .rarity(Rarity.COMMON));
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, @NonNull LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel serverLevel) {
            // 幼年生物不记录
            if (target.isBaby()) {
                super.hurtEnemy(stack, target, attacker);
                return;
            }

            if (attacker instanceof Player) {
                handleCrossbreeding(stack, target, serverLevel);
            } else if (attacker instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                handleZombieAttack(stack, target, zombie, serverLevel);
            }
        }
        super.hurtEnemy(stack, target, attacker);
    }

    // ==================== 僵尸攻击逻辑 ====================

    /**
     * 僵尸手持矛攻击时的特殊逻辑：
     * 第一击对玩家/村民 → 记录 + 寻找附近敌对生物连招
     * 第二击（已有记录）→ 完成杂交 + 清除互相仇恨
     */
    private void handleZombieAttack(ItemStack stack, LivingEntity target,
                                     net.minecraft.world.entity.monster.zombie.Zombie zombie, ServerLevel level) {
        EntityType<?> recorded = getRecordedEntity(stack);

        if (recorded == null) {
            // 第一击：只记录玩家和村民
            if (target instanceof Player
                    || target instanceof net.minecraft.world.entity.npc.villager.AbstractVillager) {
                setRecordedEntity(stack, target);
                level.playSound(null, target.blockPosition(),
                        SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.3F, 2.0F);
                triggerZombieCombo(zombie, level);
            }
        } else {
            // 第二击：完成杂交
            handleCrossbreeding(stack, target, level);
            // 清除互相仇恨，防止两只敌对生物互打
            clearMutualAggro(zombie, target);
        }
    }

    /**
     * 清除僵尸与目标间的互相仇恨，防止连招后互殴
     */
    private void clearMutualAggro(net.minecraft.world.entity.monster.zombie.Zombie zombie, LivingEntity target) {
        zombie.setTarget(null);
        zombie.setLastHurtByMob(null);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }
    }

    // ==================== 使用逻辑（启用矛的瞄准姿态） ====================

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // ==================== 核心杂交逻辑 ====================

    private void handleCrossbreeding(ItemStack stack, LivingEntity target, ServerLevel level) {
        EntityType<?> recordedType = getRecordedEntity(stack);

        if (recordedType == null) {
            // 末影龙不参与繁殖
            if (target.getType() == EntityType.ENDER_DRAGON) return;
            // 第一击：记录生物
            setRecordedEntity(stack, target);
            level.playSound(null, target.blockPosition(),
                    SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.3F, 2.0F);
        } else {
            // 不能是同一个生物
            if (isSameEntity(stack, target)) {
                clearRecordedEntity(stack);
                return;
            }

            // 第二击：杂交繁殖
            EntityType<?> offspringType = getOffspringType(recordedType, target.getType(), level.random);

            if (offspringType != null) {
                // 产物为村民时有 2/3 概率繁殖失败
                if (offspringType == EntityType.VILLAGER && level.random.nextFloat() < 0.667F) {
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            target.getX(), target.getY() + target.getEyeHeight(), target.getZ(),
                            5, 0.3D, 0.3D, 0.3D, 0.1D);
                    level.playSound(null, target.blockPosition(),
                            SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
                } else {
                    spawnOffspring(offspringType, target, level);
                }
            }

            clearRecordedEntity(stack);
        }
    }

    /**
     * 僵尸连招：记录玩家后，寻找附近敌对生物并冲过去攻击
     */
    private void triggerZombieCombo(net.minecraft.world.entity.monster.zombie.Zombie zombie, ServerLevel level) {
        List<Monster> hostiles = level.getEntitiesOfClass(
                Monster.class,
                zombie.getBoundingBox().inflate(10),
                e -> e != zombie && e.isAlive() && !e.isBaby()
        );

        if (!hostiles.isEmpty()) {
            LivingEntity nearest = hostiles.stream()
                    .min(Comparator.comparingDouble(zombie::distanceToSqr))
                    .orElse(null);

            if (nearest != null) {
                // 给予速度效果让僵尸快速冲向目标
                zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, false));
                zombie.setTarget(nearest);
            }
        }
    }

    /**
     * 根据两种生物类型计算杂交产物
     */
    private EntityType<?> getOffspringType(EntityType<?> first, EntityType<?> second, net.minecraft.util.RandomSource random) {
        // === 末影龙不参与繁殖，固定生出另一半 ===
        if (first == EntityType.ENDER_DRAGON) return second;
        if (second == EntityType.ENDER_DRAGON) return first;

        // === 特殊配对（双向判断，调用顺序无关） ===

        // 类玩家（村民/玩家） + 亡灵（僵尸/尸壳/溺尸/僵尸村民） → 僵尸村民
        if (isHumanoid(first) && isUndead(second) || isHumanoid(second) && isUndead(first)) {
            return EntityType.ZOMBIE_VILLAGER;
        }

        // 亡灵 + 猪 → 僵尸猪灵
        if (isUndead(first) && second == EntityType.PIG || isUndead(second) && first == EntityType.PIG) {
            return EntityType.ZOMBIFIED_PIGLIN;
        }

        // 类玩家 + 猪 → 猪灵
        if (isHumanoid(first) && second == EntityType.PIG || isHumanoid(second) && first == EntityType.PIG) {
            return EntityType.PIGLIN;
        }

        // 马 + 骷髅类 → 骷髅马（幼年）
        if (first == EntityType.HORSE && isSkeletonLike(second)
                || second == EntityType.HORSE && isSkeletonLike(first)) {
            return EntityType.SKELETON_HORSE;
        }

        // 马 + 亡灵 → 僵尸马（幼年）
        if (first == EntityType.HORSE && isUndead(second)
                || second == EntityType.HORSE && isUndead(first)) {
            return EntityType.ZOMBIE_HORSE;
        }

        // 马 + 驴 → 骡
        if (first == EntityType.HORSE && second == EntityType.DONKEY
                || first == EntityType.DONKEY && second == EntityType.HORSE) {
            return EntityType.MULE;
        }

        // 烈焰人 + 史莱姆 → 岩浆怪
        if (first == EntityType.BLAZE && second == EntityType.SLIME
                || first == EntityType.SLIME && second == EntityType.BLAZE) {
            return EntityType.MAGMA_CUBE;
        }

        // 史莱姆 + 岩浆怪 → 随机
        if (first == EntityType.SLIME && second == EntityType.MAGMA_CUBE
                || first == EntityType.MAGMA_CUBE && second == EntityType.SLIME) {
            return random.nextBoolean() ? EntityType.SLIME : EntityType.MAGMA_CUBE;
        }

        // 牛 + 哞菇 → 哞菇
        if (first == EntityType.COW && second == EntityType.MOOSHROOM
                || first == EntityType.MOOSHROOM && second == EntityType.COW) {
            return EntityType.MOOSHROOM;
        }

        // 蠹虫 + 末影人 → 末影螨
        if (first == EntityType.SILVERFISH && second == EntityType.ENDERMAN
                || first == EntityType.ENDERMAN && second == EntityType.SILVERFISH) {
            return EntityType.ENDERMITE;
        }

        // 蠹虫 + 蠹虫 → 末影螨
        if (first == EntityType.SILVERFISH && second == EntityType.SILVERFISH) {
            return EntityType.ENDERMITE;
        }

        // === 同种生物 → 直接生成 ===
        if (first == second) {
            // 产物为青蛙时替换为蝌蚪
            return first == EntityType.FROG ? EntityType.TADPOLE : first;
        }

        // === 兜底：随机二选一（排除不可生成的类型） ===
        boolean firstSpawnable = canSpawn(first);
        boolean secondSpawnable = canSpawn(second);

        EntityType<?> result;
        if (!firstSpawnable && !secondSpawnable) result = null;
        else if (!firstSpawnable) result = second;
        else if (!secondSpawnable) result = first;
        else result = random.nextBoolean() ? first : second;

        // 产物为青蛙时替换为蝌蚪
        if (result == EntityType.FROG) {
            return EntityType.TADPOLE;
        }
        return result;
    }

    /**
     * 生成杂交产物
     */
    private void spawnOffspring(EntityType<?> type, LivingEntity at, ServerLevel level) {
        Entity offspring = type.create(level, EntitySpawnReason.EVENT);
        if (offspring == null) return;

        offspring.setPos(at.getX(), at.getY(), at.getZ());

        // 如果可以设为幼年，就设为幼年
        // Zombie 覆盖: 僵尸、尸壳、溺尸、僵尸村民、僵尸猪灵
        // Piglin 单独覆盖: 猪灵
        // AgeableMob 覆盖: 牛、猪、羊、马、驴、骡等可繁殖动物
        // PiglinBrute 无 setBaby，不需要单独处理
        if (offspring instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
            zombie.setBaby(true);
        } else if (offspring instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            piglin.setBaby(true);
        } else if (offspring instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }

        level.addFreshEntity(offspring);

        // 爱心粒子效果
        level.sendParticles(ParticleTypes.HEART,
                at.getX(), at.getY() + at.getEyeHeight(), at.getZ(),
                10, 0.4D, 0.4D, 0.4D, 0.1D);

        // 音效
        level.playSound(null, at.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.5F);
    }

    // ==================== 辅助判断方法 ====================

    /** 是否类人生物（村民或玩家） */
    private static boolean isHumanoid(EntityType<?> type) {
        return type == EntityType.VILLAGER || type == EntityType.PLAYER;
    }

    /** 是否骷髅类生物 */
    private static boolean isSkeletonLike(EntityType<?> type) {
        return type == EntityType.SKELETON || type == EntityType.STRAY;
    }

    /** 是否亡灵生物 */
    private static boolean isUndead(EntityType<?> type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.HUSK
                || type == EntityType.DROWNED
                || type == EntityType.ZOMBIE_VILLAGER;
    }

    /** 能否通过 create 生成 */
    private static boolean canSpawn(EntityType<?> type) {
        return type != EntityType.PLAYER;
    }

    // ==================== NBT 读写 ====================

    /**
     * 获取矛上记录的生物类型
     */
    public static EntityType<?> getRecordedEntity(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        String id = tag.getString("RecordedEntity").orElse("");
        if (id.isEmpty()) return null;

        Identifier key = Identifier.tryParse(id);
        if (key == null) return null;

        return BuiltInRegistries.ENTITY_TYPE.get(key)
                .map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }

    /**
     * 获取矛上记录的生物 UUID
     */
    public static UUID getRecordedEntityUUID(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        String uuidStr = tag.getString("RecordedEntityUUID").orElse("");
        if (uuidStr.isEmpty()) return null;

        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 记录生物类型和 UUID 到矛上
     */
    public static void setRecordedEntity(ItemStack stack, LivingEntity entity) {
        EntityType<?> type = entity.getType();
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        CompoundTag tag = new CompoundTag();
        tag.putString("RecordedEntity", key.toString());
        tag.putString("RecordedEntityUUID", entity.getUUID().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * 检查目标是否与记录的生物是同一个实体
     */
    private static boolean isSameEntity(ItemStack stack, LivingEntity target) {
        UUID recordedUUID = getRecordedEntityUUID(stack);
        return recordedUUID != null && recordedUUID.equals(target.getUUID());
    }

    /**
     * 清除矛上记录的生物
     */
    public static void clearRecordedEntity(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag tag = data.copyTag();
        tag.remove("RecordedEntity");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ==================== 工具提示 ====================

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay tooltipDisplay, @NonNull Consumer<Component> tooltipAdder,
                                @NonNull TooltipFlag flag) {
        EntityType<?> recorded = getRecordedEntity(stack);
        if (recorded != null) {
            Component name = recorded.getDescription().copy().withStyle(ChatFormatting.GRAY);
            tooltipAdder.accept(Component.translatable(
                    "tooltip.hasoook.end_rod_spear.recorded", name)
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
    }

    // ==================== 蓄力动画 ====================

    @Override
    public int getUseDuration(@NonNull ItemStack itemStack, @NonNull LivingEntity livingEntity) {
        return 72000;
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack itemStack) {
        return ItemUseAnimation.SPEAR;
    }
}

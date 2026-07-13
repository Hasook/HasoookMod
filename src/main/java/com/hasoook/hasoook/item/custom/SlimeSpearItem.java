package com.hasoook.hasoook.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.UnknownNullability;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

import static net.minecraft.world.item.component.KineticWeapon.getMotion;

public class SlimeSpearItem extends Item {
    public SlimeSpearItem(Properties properties) {
        super(properties.spear(ToolMaterial.WOOD, 0.75F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
                .durability(40)
                .repairable(Items.SLIME_BALL));
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, LivingEntity target, @NonNull LivingEntity attacker) {
        if (target.level() instanceof ServerLevel serverLevel) {
            ItemStack storedItem = getStoredItem(stack);
            Random random = new Random();
            InteractionHand hand = attacker.getUsedItemHand(); // 获取玩家使用物品的手

            if (!storedItem.isEmpty()) {
                // 粒子效果（黏着的物品破碎的粒子）
                serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, storedItem),
                        target.getX(), target.getY() + target.getEyeHeight() / 2, target.getZ(),
                        10, 0.4D, 0.4D, 0.4D, 0.1D);

                // 根据物品触发特殊效果
                handleSpecialItems(stack, storedItem, target, attacker, serverLevel, random);

                // 触发存储物品的伤害效果
                Item stored = storedItem.getItem();
                try {
                    stored.hurtEnemy(storedItem, target, attacker);
                } catch (Exception e) {
                    // 如果物品不是武器，直接忽略
                }

                if (attacker instanceof Player player) {
                    ItemStack original = player.getItemInHand(hand);

                    // 尝试调用物品的使用逻辑
                    try {
                        ItemStack temp = storedItem.copy();
                        player.setItemInHand(hand, temp);

                        InteractionResult result = temp.use(player.level(), player, hand);

                        // 只有使用成功，并且不是有耐久的物品，才清空黏着的物品
                        if (result.consumesAction() && storedItem.getMaxDamage() < 1) {
                            clearStoredItem(stack);
                        }
                    } finally {
                        // 恢复玩家手上的原物品（有些物品的使用逻辑会清空玩家的手持物品）
                        player.setItemInHand(hand, original);
                    }

                    // 尝试调用物品的互动实体逻辑
                    try {
                        InteractionResult result = storedItem.getItem().interactLivingEntity(
                                storedItem,
                                player,
                                target,
                                hand
                        );
                        result.consumesAction();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void inventoryTick(@NonNull ItemStack stack, ServerLevel level, @NonNull Entity entity, @Nullable EquipmentSlot slot) {
        if (level.isClientSide()) return;

        if (!(entity instanceof Player player)) return;

        // 如果没有黏着物品则跳过
        ItemStack storedItem = getStoredItem(stack);
        if (storedItem.isEmpty()) return;

        // 检查玩家是否正在使用此物品
        if (!player.isUsingItem() || player.getUseItem() != stack) {
            return;
        }

        // 如果超过了最大使用时间则跳过
        int useTick = getUseDuration(stack, player) - player.getUseItemRemainingTicks();
        if (useTick >= getMaxActiveTicks(stack)) {
            return;
        }

        // 获取玩家瞄准的位置
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        // 尝试使用黏着的物品在方块上使用
        if (!(storedItem.getItem() instanceof BlockItem) && hit.getType() == HitResult.Type.BLOCK) {
            // 检查是否是可用在方块上的物品
            InteractionResult result = tryUseItemOnBlock(level, player, storedItem, hit, slot);

            if (result.consumesAction()) {
                if (storedItem.isDamageableItem() && slot != null) {
                    stack.hurtAndBreak(1, player, slot);
                } else {
                    clearStoredItem(stack);
                }
            }
        }

        // 尝试使用蜂蜜块拖动生物
        if (storedItem.is(Items.HONEY_BLOCK)) {
            // 实体射线检测
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    level,
                    player,
                    player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(5.0D)),
                    player.getBoundingBox()
                            .expandTowards(player.getLookAngle().scale(5.0D))
                            .inflate(1.0D),
                    e -> e instanceof LivingEntity && e != player,
                    0.3F
            );

            if (entityHit != null) {
                Entity target = entityHit.getEntity();
                Vec3 holdPos = player.getEyePosition().add(player.getLookAngle().scale(3.0D));
                Vec3 delta = holdPos.subtract(target.position().add(0, target.getBbHeight() * 0.5, 0));
                double strength = 0.2; // 跟随强度
                target.setDeltaMovement(target.getDeltaMovement().scale(0.5).add(delta.x * strength, delta.y * strength, delta.z * strength));
                target.hurtMarked = true;

                if (player.tickCount % 40 == 0) {
                    stack.hurtAndBreak(1, player, player.getUsedItemHand().asEquipmentSlot());
                }

                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, storedItem), target.getX(), target.getY() + target.getEyeHeight() / 2, target.getZ(), 1, 0.4D, 0.4D, 0.4D, 0.01D);
            }
        }

        // 尝试用切石机切割生物
        if (storedItem.is(Items.STONECUTTER)) {
            // 以玩家视线前方 3 格作为中心
            Vec3 center = player.getEyePosition()
                    .add(player.getLookAngle().scale(3.0D));

            // 构建范围盒子
            AABB box = new AABB(
                    center.x - 1, center.y - 1, center.z - 1,
                    center.x + 1, center.y + 1, center.z + 1
            );

            // 获取范围内生物
            List<LivingEntity> targets = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    e -> e != player && player.hasLineOfSight(e)
            );

            if (!targets.isEmpty() && player.tickCount % 10 == 0) {

                for (LivingEntity target : targets) {

                    target.hurt(level.damageSources().playerAttack(player), 2.0F);

                    // 粒子
                    level.sendParticles(
                            ParticleTypes.CRIT,
                            target.getX(),
                            target.getY() + target.getBbHeight() * 0.5,
                            target.getZ(),
                            5,
                            0.2, 0.2, 0.2,
                            0.01
                    );
                }

                // 消耗耐久
                stack.hurtAndBreak(1, player, player.getUsedItemHand().asEquipmentSlot());
            }
        }

        super.inventoryTick(stack, level, entity, slot);
    }

    private void handleSpecialItems(ItemStack stack,ItemStack storedItem, LivingEntity target, LivingEntity attacker, ServerLevel serverLevel, Random random) {
        // 一次性点燃效果
        if (storedItem.is(Items.FIRE_CHARGE)) {
            target.setRemainingFireTicks(320);
            clearStoredItem(stack);

            // 点燃效果
        } else if (storedItem.is(Items.FLINT_AND_STEEL) ||
                storedItem.is(Items.CAMPFIRE) ||
                storedItem.is(Items.SOUL_CAMPFIRE) ||
                storedItem.is(Items.LAVA_BUCKET)) {
            target.setRemainingFireTicks(160);

            // TNT爆炸效果
        } else if (storedItem.is(Items.TNT) || storedItem.is(Items.TNT_MINECART)) {
            PrimedTnt tnt = new PrimedTnt(serverLevel, target.getX(), target.getY(), target.getZ(), attacker);
            int fuseTick = (int) Math.max(0, (40 - getRelativeSpeed(attacker, target))); // 根据速度计算爆炸时间
            tnt.setFuse(fuseTick);
            serverLevel.addFreshEntity(tnt);
            clearStoredItem(stack);

            // 床效果，如果是地狱或者末地，就爆炸
        } else if (storedItem.getItem() instanceof BedItem) {
            // 只在下界或末地生效
            if (serverLevel.dimension() == Level.NETHER || serverLevel.dimension() == Level.END) {
                serverLevel.explode(
                        attacker,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        5.0F,
                        Level.ExplosionInteraction.BLOCK // 破坏方块
                );
                clearStoredItem(stack);
            }

            // 蜂巢召唤蜜蜂
        } else if (storedItem.is(Items.BEE_NEST) && random.nextFloat() < 0.4) {
            Bee bee = new Bee(EntityType.BEE, serverLevel);
            bee.setPos(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
            bee.setTarget(target);
            bee.setAge(18000);
            serverLevel.addFreshEntity(bee);

            // 食物效果，直接给目标喂食
        } else if (storedItem.get(DataComponents.FOOD) != null) {
            ItemStack foodStack = storedItem.copy();
            foodStack.finishUsingItem(target.level(), target);
            clearStoredItem(stack);

            // 避雷针生成闪电效果
        } else if (storedItem.is(Items.LIGHTNING_ROD) && serverLevel.isThundering() && random.nextFloat() < 0.4) {
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.spawn(serverLevel, BlockPos.containing(target.position()), EntitySpawnReason.TRIGGERED);
            if (lightning != null) {
                lightning.setPos(target.getX(), target.getY(), target.getZ());
                lightning.setCause(attacker instanceof ServerPlayer sp ? sp : null);
            }

            // 萤石粉给发光效果
        } else if (storedItem.is(Items.GLOWSTONE_DUST)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, true));

            // 发光方块会给目标发光效果
        } else if (storedItem.getItem() instanceof BlockItem blockItem) {
            int light = blockItem.getBlock().defaultBlockState().getLightEmission();
            if (light > 10) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * light, 0, false, true));
            }
        }
    }

    /**
     * 尝试在方块上使用物品
     */
    private InteractionResult tryUseItemOnBlock(ServerLevel level, Player player, ItemStack storedItem, BlockHitResult hit, EquipmentSlot slot) {
        InteractionHand hand = slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        ItemStack original = player.getItemInHand(hand); // 保存手上的矛

        ItemStack temp = storedItem.copy();
        player.setItemInHand(hand, temp);

        try {
            InteractionResult result = temp.use(level, player, hand);

            // 成功使用并且不是耐久物品，才清空存储物品
            if (result.consumesAction() && storedItem.getMaxDamage() < 1) {
                clearStoredItem(original); // 注意这里要清空存储的物品，而不是 temp
            }

            return result;
        } finally {
            // 恢复手上的矛
            player.setItemInHand(hand, original);
        }
    }

    /**
    获取附加在矛上的物品
    @param spear 矛物品栈
    @return 附加在矛上的物品栈
    */
    public static ItemStack getStoredItem(ItemStack spear) {
        CustomData data = spear.get(DataComponents.CUSTOM_DATA);
        if (data == null) return ItemStack.EMPTY;

        CompoundTag tag = data.copyTag();

        String id = tag.getString("AttachedItem").orElse("");
        if (id.isEmpty()) return ItemStack.EMPTY;

        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(key).map(Holder.Reference::value).orElse(Items.AIR);

        if (item == Items.AIR) return ItemStack.EMPTY;

        return new ItemStack(item);
    }

    /**
     * 给 SlimeSpearItem 设置附加物品
     * @param spear 黏液球矛的 ItemStack
     * @param item 要附加的物品
     */
    public static void setStoredItem(ItemStack spear, @UnknownNullability Item item) {
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        CompoundTag tag = new CompoundTag();
        tag.putString("AttachedItem", itemId);
        spear.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateAttributes(spear);
    }

    /**
     * 移除 SlimeSpearItem 的附加物品
     * @param spear 黏液球矛的 ItemStack
     */
    public static void clearStoredItem(ItemStack spear) {
        CustomData data = spear.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag tag = data.copyTag();
        tag.remove("AttachedItem"); // 移除字段
        spear.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // 动态更新工具属性
    public static void updateAttributes(ItemStack spear) {
        ItemStack stored = getStoredItem(spear);

        float baseDamage = ToolMaterial.WOOD.attackDamageBonus(); // 基础伤害
        float storedDamage = getAttackDamageFromItem(stored); // 附加物的伤害

        ItemAttributeModifiers modifiers =
                ItemAttributeModifiers.builder()
                        // 攻击伤害
                        .add(
                                Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(
                                        Item.BASE_ATTACK_DAMAGE_ID,
                                        baseDamage + storedDamage,
                                        AttributeModifier.Operation.ADD_VALUE
                                ),
                                EquipmentSlotGroup.MAINHAND
                        )

                        // 攻击速度
                        .add(
                                Attributes.ATTACK_SPEED,
                                new AttributeModifier(
                                        Item.BASE_ATTACK_SPEED_ID,
                                        (1.0F / 0.75F) - 4.0F,
                                        AttributeModifier.Operation.ADD_VALUE
                                ),
                                EquipmentSlotGroup.MAINHAND
                        )

                        // 击退
                        .add(
                                Attributes.ATTACK_KNOCKBACK,
                                new AttributeModifier(
                                        Item.BASE_ATTACK_SPEED_ID,
                                        calculateKnockback(stored),
                                        AttributeModifier.Operation.ADD_VALUE
                                ),
                                EquipmentSlotGroup.MAINHAND
                        )

                        // 方块互动距离
                        .add(
                                Attributes.BLOCK_INTERACTION_RANGE,
                                new AttributeModifier(
                                        Identifier.fromNamespaceAndPath(
                                                "hasoook",
                                                "slimeball_spear_block_reach"
                                        ),
                                        getReachBonusFromItem(stored),
                                        AttributeModifier.Operation.ADD_VALUE
                                ),
                                EquipmentSlotGroup.MAINHAND
                        )
                        .build();

        // 矛攻击距离
        float baseNormalMax = 4.5F;
        float baseChargedMax = 6.5F;

        float finalNormal = (float)(baseNormalMax + getReachBonusFromItem(stored)); // 正常攻击距离
        float finalCharged = (float)(baseChargedMax + getReachBonusFromItem(stored)); // 蓄力攻击距离

        spear.set(
                DataComponents.ATTACK_RANGE,
                new AttackRange(
                        2.0F,
                        finalNormal,
                        2.0F,
                        finalCharged,
                        0.125F,
                        0.5F
                )
        );

        spear.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
    }

    public static float getAttackDamageFromItem(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);

        if (modifiers == null) return 0F;

        final float[] damage = {0F};

        modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += (float) modifier.amount();
            }
        });

        damage[0] += getDefaultDestroyTime(stack);

        return damage[0];
    }

    // 计算方块的伤害
    public static float getDefaultDestroyTime(ItemStack stack) {
        // 如果是方块
        if (stack.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().defaultDestroyTime() / 10;

            // 负数或超过10的处理
            if (hardness < 0) {
                hardness = 9F;
            } else if (hardness > 5) {
                hardness = 5F;
            }

            return hardness;
        }
        return 0;
    }

    // 计算方块的击退等级
    public static float calculateKnockback(ItemStack stack) {
        // 如果是方块
        if (stack.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().defaultDestroyTime() / 10;

            // 负数或超过10的处理
            if (hardness < 0 || stack.is(Items.SLIME_BLOCK)) {
                hardness = 5F;
            } else if (hardness > 2) {
                hardness = 2F;
            }

            return hardness;
        }
        return 0;
    }

    public static double getReachBonusFromItem(ItemStack stack) {
        if (stack.isEmpty()) return 1.0D;

        Item item = stack.getItem();

        // 矛类
        if (item.getUseAnimation(stack) == ItemUseAnimation.SPEAR) {
            return 2.5D;
        }

        // 工具和武器
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
            return 1.0D;
        }

        // 方块
        if (item instanceof BlockItem) {
            return 0.5D;
        }

        // 其它物品
        return 0.0D;
    }


    /**
     * 计算相对速度
     *
     * @param attacker 攻击者（通常是玩家）
     * @param target   被攻击实体
     * @return 相对速度（格/秒，永远 >= 0）
     */
    public static double getRelativeSpeed(LivingEntity attacker, Entity target) {
        Vec3 look = attacker.getLookAngle(); // 攻击方向（单位向量）

        double attackerSpeed = look.dot(getMotion(attacker)); // 攻击者前向速度

        double targetSpeed = look.dot(getMotion(target)); // 目标前向速度

        // 相对速度：只取正值
        return Math.max(0.0D, attackerSpeed - targetSpeed);
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

    // 获取最大使用时间
    private static int getMaxActiveTicks(ItemStack stack) {
        KineticWeapon kinetic = getKinetic(stack);
        if (kinetic == null) return Integer.MAX_VALUE;

        return kinetic.computeDamageUseDuration();
    }
}
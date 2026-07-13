package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BedItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.consume.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class SlimeSpearItem extends Item {
    public SlimeSpearItem(Settings settings) {
        super(settings
                .spear(ToolMaterial.WOOD, 0.75F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
                .maxDamage(40)
                .repairable(Items.SLIME_BALL));
    }

    // ==================== 攻击效果 ====================

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.getEntityWorld() instanceof ServerWorld serverWorld) {
            ItemStack storedItem = getStoredItem(stack);
            Random random = new Random();
            Hand hand = attacker.getActiveHand();

            if (!storedItem.isEmpty()) {
                // 粒子效果（黏着的物品破碎的粒子）
                serverWorld.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, storedItem),
                        target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                        10, 0.4D, 0.4D, 0.4D, 0.1D);

                // 根据物品触发特殊效果
                handleSpecialItems(stack, storedItem, target, attacker, serverWorld, random);

                // 触发存储物品的伤害效果
                Item stored = storedItem.getItem();
                try {
                    stored.postHit(storedItem, target, attacker);
                } catch (Exception e) {
                    // 如果物品不是武器，直接忽略
                }

                if (attacker instanceof PlayerEntity player) {
                    ItemStack original = player.getStackInHand(hand);

                    // 尝试调用物品的使用逻辑
                    try {
                        ItemStack temp = storedItem.copy();
                        player.setStackInHand(hand, temp);

                        ActionResult result = temp.use(player.getEntityWorld(), player, hand);

                        // 只有使用成功，并且不是有耐久的物品，才清空黏着的物品
                        if (result.isAccepted() && storedItem.getMaxDamage() < 1) {
                            clearStoredItem(stack);
                        }
                    } finally {
                        // 恢复玩家手上的原物品
                        player.setStackInHand(hand, original);
                    }

                    // 尝试调用物品的互动实体逻辑
                    try {
                        ActionResult result = storedItem.getItem().useOnEntity(
                                storedItem, player, target, hand);
                        result.isAccepted();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        super.postHit(stack, target, attacker);
    }

    // ==================== 库存Tick: 持续使用时的效果 ====================

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (world.isClient()) return;

        if (!(entity instanceof PlayerEntity player)) return;

        // 如果没有黏着物品则跳过
        ItemStack storedItem = getStoredItem(stack);
        if (storedItem.isEmpty()) return;

        // 检查玩家是否正在使用此物品
        if (!player.isUsingItem() || player.getActiveItem() != stack) {
            return;
        }

        // 如果超过了最大使用时间则跳过
        int useTick = getMaxUseTime(stack, player) - player.getItemUseTimeLeft();
        if (useTick >= getMaxActiveTicks(stack)) {
            return;
        }

        // 获取玩家瞄准的位置
        BlockHitResult hit = Item.raycast(world, player, RaycastContext.FluidHandling.NONE);

        // 尝试使用黏着的物品在方块上使用
        if (!(storedItem.getItem() instanceof BlockItem) && hit.getType() == HitResult.Type.BLOCK) {
            ActionResult result = tryUseItemOnBlock(world, player, storedItem, hit, slot);

            if (result.isAccepted()) {
                if (storedItem.isDamageable() && slot != null) {
                    stack.damage(1, player, slot);
                } else {
                    clearStoredItem(stack);
                }
            }
        }

        // 尝试使用蜂蜜块拖动生物
        if (storedItem.isOf(Items.HONEY_BLOCK)) {
            EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                    world, player,
                    player.getEyePos(),
                    player.getEyePos().add(player.getRotationVector().multiply(5.0D)),
                    player.getBoundingBox()
                            .stretch(player.getRotationVector().multiply(5.0D))
                            .expand(1.0D),
                    e -> e instanceof LivingEntity && e != player,
                    0.3F
            );

            if (entityHit != null) {
                Entity target = entityHit.getEntity();
                Vec3d holdPos = player.getEyePos().add(player.getRotationVector().multiply(3.0D));
                Vec3d delta = holdPos.subtract(target.getEntityPos().add(0, target.getHeight() * 0.5, 0));
                double strength = 0.2;
                target.setVelocity(target.getVelocity().multiply(0.5).add(delta.x * strength, delta.y * strength, delta.z * strength));
                target.velocityDirty = true;

                if (player.age % 40 == 0) {
                    stack.damage(1, player, slot);
                }

                world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, storedItem),
                        target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                        1, 0.4D, 0.4D, 0.4D, 0.01D);
            }
        }

        // 尝试用切石机切割生物
        if (storedItem.isOf(Items.STONECUTTER)) {
            Vec3d center = player.getEyePos()
                    .add(player.getRotationVector().multiply(3.0D));

            Box box = new Box(
                    center.x - 1, center.y - 1, center.z - 1,
                    center.x + 1, center.y + 1, center.z + 1
            );

            List<LivingEntity> targets = world.getEntitiesByClass(
                    LivingEntity.class, box,
                    e -> e != player && player.canSee(e)
            );

            if (!targets.isEmpty() && player.age % 10 == 0) {
                for (LivingEntity t : targets) {
                    t.damage(world, world.getDamageSources().playerAttack(player), 2.0F);

                    world.spawnParticles(
                            ParticleTypes.CRIT,
                            t.getX(), t.getY() + t.getHeight() * 0.5, t.getZ(),
                            5, 0.2, 0.2, 0.2, 0.01
                    );
                }
                stack.damage(1, player, slot);
            }
        }

        super.inventoryTick(stack, world, entity, slot);
    }

    // ==================== 特殊物品效果处理 ====================

    private void handleSpecialItems(ItemStack spear, ItemStack storedItem, LivingEntity target,
                                     LivingEntity attacker, ServerWorld serverWorld, Random random) {
        // 一次性点燃效果
        if (storedItem.isOf(Items.FIRE_CHARGE)) {
            target.setFireTicks(320);
            clearStoredItem(spear);

        // 点燃效果
        } else if (storedItem.isOf(Items.FLINT_AND_STEEL) ||
                storedItem.isOf(Items.CAMPFIRE) ||
                storedItem.isOf(Items.SOUL_CAMPFIRE) ||
                storedItem.isOf(Items.LAVA_BUCKET)) {
            target.setFireTicks(160);

        // TNT爆炸效果
        } else if (storedItem.isOf(Items.TNT) || storedItem.isOf(Items.TNT_MINECART)) {
            TntEntity tnt = new TntEntity(serverWorld, target.getX(), target.getY(), target.getZ(), attacker);
            int fuseTick = (int) Math.max(0, (40 - getRelativeSpeed(attacker, target)));
            tnt.setFuse(fuseTick);
            serverWorld.spawnEntity(tnt);
            clearStoredItem(spear);

        // 床效果，如果是地狱或者末地，就爆炸
        } else if (storedItem.getItem() instanceof BedItem) {
            RegistryKey<World> dim = serverWorld.getRegistryKey();
            if (dim == World.NETHER || dim == World.END) {
                serverWorld.createExplosion(
                        attacker,
                        target.getX(), target.getY(), target.getZ(),
                        5.0F,
                        World.ExplosionSourceType.BLOCK
                );
                clearStoredItem(spear);
            }

        // 蜂巢召唤蜜蜂
        } else if (storedItem.isOf(Items.BEE_NEST) && random.nextFloat() < 0.4) {
            BeeEntity bee = new BeeEntity(EntityType.BEE, serverWorld);
            bee.setPosition(target.getX(), target.getY() + target.getHeight(), target.getZ());
            bee.setTarget(target);
            bee.setBreedingAge(18000);
            serverWorld.spawnEntity(bee);

        // 食物效果，直接给目标喂食
        } else if (storedItem.contains(DataComponentTypes.FOOD)) {
            ItemStack foodStack = storedItem.copy();
            foodStack.finishUsing(target.getEntityWorld(), target);
            clearStoredItem(spear);

        // 避雷针生成闪电效果
        } else if (storedItem.isOf(Items.LIGHTNING_ROD) && serverWorld.isThundering() && random.nextFloat() < 0.4) {
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.spawn(
                    serverWorld, BlockPos.ofFloored(target.getEntityPos()), SpawnReason.TRIGGERED);
            if (lightning != null) {
                lightning.setPosition(target.getX(), target.getY(), target.getZ());
                lightning.setChanneler(attacker instanceof ServerPlayerEntity sp ? sp : null);
            }

        // 萤石粉给发光效果
        } else if (storedItem.isOf(Items.GLOWSTONE_DUST)) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, false, true));

        // 发光方块会给目标发光效果
        } else if (storedItem.getItem() instanceof BlockItem blockItem) {
            BlockState blockState = blockItem.getBlock().getDefaultState();
            int light = blockState.getLuminance();
            if (light > 10) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * light, 0, false, true));
            }
        }
    }

    // ==================== 方块使用辅助方法 ====================

    private ActionResult tryUseItemOnBlock(ServerWorld world, PlayerEntity player, ItemStack storedItem,
                                            BlockHitResult hit, EquipmentSlot slot) {
        Hand hand = slot == EquipmentSlot.MAINHAND ? Hand.MAIN_HAND : Hand.OFF_HAND;

        ItemStack original = player.getStackInHand(hand);
        ItemStack temp = storedItem.copy();
        player.setStackInHand(hand, temp);

        try {
            ActionResult result = temp.use(world, player, hand);

            if (result.isAccepted() && storedItem.getMaxDamage() < 1) {
                clearStoredItem(original);
            }

            return result;
        } finally {
            player.setStackInHand(hand, original);
        }
    }

    // ==================== 存储物品管理 ====================

    /**
     * 获取附加在矛上的物品
     */
    public static ItemStack getStoredItem(ItemStack spear) {
        NbtComponent data = spear.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return ItemStack.EMPTY;

        NbtCompound tag = data.copyNbt();
        if (!tag.contains("AttachedItem")) return ItemStack.EMPTY;

        String id = tag.getString("AttachedItem", "");
        if (id.isEmpty()) return ItemStack.EMPTY;

        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) return ItemStack.EMPTY;

        Item item = Registries.ITEM.get(identifier);
        if (item == Items.AIR) return ItemStack.EMPTY;

        return new ItemStack(item);
    }

    /**
     * 给 SlimeSpearItem 设置附加物品
     */
    public static void setStoredItem(ItemStack spear, Item item) {
        String itemId = Registries.ITEM.getId(item).toString();
        NbtCompound tag = new NbtCompound();
        tag.putString("AttachedItem", itemId);
        spear.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        updateAttributes(spear);
    }

    /**
     * 移除 SlimeSpearItem 的附加物品
     */
    public static void clearStoredItem(ItemStack spear) {
        NbtComponent data = spear.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return;

        NbtCompound tag = data.copyNbt();
        tag.remove("AttachedItem");
        spear.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    // ==================== 属性计算 ====================

    /**
     * 动态更新矛的工具属性
     */
    public static void updateAttributes(ItemStack spear) {
        ItemStack stored = getStoredItem(spear);

        float baseDamage = ToolMaterial.WOOD.attackDamageBonus();
        float storedDamage = getAttackDamageFromItem(stored);

        AttributeModifiersComponent modifiers =
                AttributeModifiersComponent.builder()
                        .add(
                                EntityAttributes.ATTACK_DAMAGE,
                                new EntityAttributeModifier(
                                        Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                                        baseDamage + storedDamage,
                                        EntityAttributeModifier.Operation.ADD_VALUE
                                ),
                                AttributeModifierSlot.MAINHAND
                        )
                        .add(
                                EntityAttributes.ATTACK_SPEED,
                                new EntityAttributeModifier(
                                        Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                                        (1.0F / 0.75F) - 4.0F,
                                        EntityAttributeModifier.Operation.ADD_VALUE
                                ),
                                AttributeModifierSlot.MAINHAND
                        )
                        .add(
                                EntityAttributes.ATTACK_KNOCKBACK,
                                new EntityAttributeModifier(
                                        Hasoook.id("slimeball_spear_knockback"),
                                        calculateKnockback(stored),
                                        EntityAttributeModifier.Operation.ADD_VALUE
                                ),
                                AttributeModifierSlot.MAINHAND
                        )
                        .add(
                                EntityAttributes.BLOCK_INTERACTION_RANGE,
                                new EntityAttributeModifier(
                                        Hasoook.id("slimeball_spear_block_reach"),
                                        getReachBonusFromItem(stored),
                                        EntityAttributeModifier.Operation.ADD_VALUE
                                ),
                                AttributeModifierSlot.MAINHAND
                        )
                        .build();

        float baseNormalMax = 4.5F;
        float baseChargedMax = 6.5F;

        float finalNormal = (float) (baseNormalMax + getReachBonusFromItem(stored));
        float finalCharged = (float) (baseChargedMax + getReachBonusFromItem(stored));

        spear.set(
                DataComponentTypes.ATTACK_RANGE,
                new AttackRangeComponent(
                        2.0F, finalNormal, 2.0F, finalCharged, 0.125F, 0.5F
                )
        );

        spear.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
    }

    public static float getAttackDamageFromItem(ItemStack stack) {
        if (stack.isEmpty()) return 0F;

        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            float damage = (float) modifiers.applyOperations(
                    EntityAttributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND
            );
            return damage + getDefaultDestroyTime(stack);
        }
        return getDefaultDestroyTime(stack);
    }

    /**
     * 根据方块硬度计算额外伤害
     */
    public static float getDefaultDestroyTime(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().getHardness() / 10;

            if (hardness < 0) {
                hardness = 9F;
            } else if (hardness > 5) {
                hardness = 5F;
            }
            return hardness;
        }
        return 0;
    }

    /**
     * 计算击退等级
     */
    public static float calculateKnockback(ItemStack stored) {
        if (stored.isEmpty()) return 0F;

        // 方块：按硬度计算击退
        if (stored.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().getHardness() / 10;

            if (hardness < 0 || stored.isOf(Items.SLIME_BLOCK)) {
                hardness = 5F;
            } else if (hardness > 2) {
                hardness = 2F;
            }
            return hardness;
        }

        // 非方块物品不提供击退
        return 0F;
    }

    /**
     * 计算攻击距离加成
     */
    public static double getReachBonusFromItem(ItemStack stored) {
        if (stored.isEmpty()) return 1.0D;

        Item item = stored.getItem();

        // 矛类物品
        if (item.getUseAction(stored) == UseAction.SPEAR) {
            return 2.5D;
        }

        // 工具和武器
        if (stored.contains(DataComponentTypes.TOOL) || stored.contains(DataComponentTypes.WEAPON)) {
            return 1.0D;
        }

        // 方块
        if (item instanceof BlockItem) {
            return 0.5D;
        }

        return 0.0D;
    }

    /**
     * 计算攻击者与目标之间的相对速度
     */
    public static double getRelativeSpeed(LivingEntity attacker, Entity target) {
        Vec3d look = attacker.getRotationVector();

        double attackerSpeed = look.dotProduct(KineticWeaponComponent.getAmplifiedMovement(attacker));
        double targetSpeed = look.dotProduct(KineticWeaponComponent.getAmplifiedMovement(target));

        return Math.max(0.0D, attackerSpeed - targetSpeed);
    }

    // ==================== 使用相关 ====================

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Nullable
    private static KineticWeaponComponent getKinetic(ItemStack stack) {
        return stack.get(DataComponentTypes.KINETIC_WEAPON);
    }

    private static int getMaxActiveTicks(ItemStack stack) {
        KineticWeaponComponent kinetic = getKinetic(stack);
        if (kinetic == null) return Integer.MAX_VALUE;
        return kinetic.getUseTicks();
    }
}

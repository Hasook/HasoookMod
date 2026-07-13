package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

public class PistonSpearItem extends Item {
    private static final String ROD_COUNT = "rod_count";
    private static final String MAX_ROD_COUNT = "max_rod_count";
    private static final int DEFAULT_MAX_ROD_COUNT = 1;

    private static final Identifier BLOCK_REACH_ID = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "piston_spear_block_reach");
    private static final Identifier ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "piston_spear_attack_speed");

    public PistonSpearItem(Properties properties) {
        super(
                properties.spear(
                                ToolMaterial.STONE,
                                0.65F,  // 攻击速度（越小越快）
                                0.82F,  // 蓄力动作中的移动速度倍率
                                0.6F,   // 最大蓄力时间（秒）
                                4.5F,   // 第一段速度判定开始时间（秒）
                                10.0F,  // 第一段速度攻击伤害倍率 / 速度阈值
                                9.0F,   // 第二段速度判定开始时间（秒）
                                5.1F,   // 第二段速度攻击伤害倍率 / 速度阈值
                                13.75F, // 相对速度判定开始时间（秒）
                                4.6F    // 相对速度攻击伤害倍率 / 速度阈值
                        )
                        .component(
                                DataComponents.ATTACK_RANGE,
                                new AttackRange(
                                        0.0F,  // 生存模式最小攻击距离
                                        2.5F,  // 生存模式普通最大攻击距离
                                        0.0F,  // 创造模式最小攻击距离
                                        4.5F,  // 创造模式最大攻击距离
                                        0.125F, // 命中容差
                                        1.0F   // 非玩家实体使用时的距离倍率
                                )
                        )
                        .durability(121)
                        .repairable(Items.PISTON)
        );
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, Player player, @NonNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }

        int rodCount = getRodCount(stack); // 当前长度
        int maxRodCount = getMaxRodCount(stack); // 最大长度

        if (rodCount == maxRodCount) {
            level.playSound(player, player.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            level.playSound(player, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (!level.isClientSide()) {
            rodCount++;

            if (rodCount > maxRodCount) {
                rodCount = 0;
                player.getCooldowns().addCooldown(stack, 10);
            } else {
                player.getCooldowns().addCooldown(stack, 5);
            }

            setRodCount(stack, rodCount);

            if (rodCount > 0) {
                pushEntitiesInFront(level, player, stack); // 击退前方的生物
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 推出时击退生物
     */
    private static void pushEntitiesInFront(Level level, Player player, ItemStack stack) {
        float reach = 2.5F + getReachBonus(stack) * 1.6F; // 击退距离

        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eyePos.add(look.scale(reach));

        AABB box = player.getBoundingBox()
                .expandTowards(look.scale(reach))
                .inflate(1.0D);

        List<Entity> entities = level.getEntities(
                player,
                box,
                entity -> entity.isAlive()
                        && entity.isPickable()
                        && entity != player
        );

        for (Entity entity : entities) {
            AABB targetBox = entity.getBoundingBox().inflate(0.2D);

            if (targetBox.clip(eyePos, end).isEmpty()) {
                continue;
            }

            Vec3 pushDir = entity.position()
                    .subtract(player.position())
                    .normalize();

            if (pushDir.lengthSqr() < 0.0001D) {
                pushDir = look;
            }

            double strength = 0.6D + getReachBonus(stack) * 0.18D;

            entity.push(
                    pushDir.x * strength,
                    0.15 + pushDir.y * strength,
                    pushDir.z * strength
            );

            entity.hurtMarked = true;
        }
    }

    /**
     * 获取长度
     */
    public static int getRodCount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);

        if (data == null) {
            return 0;
        }

        CompoundTag tag = data.copyTag();

        return tag.getInt(ROD_COUNT).orElse(0);
    }

    /**
     * 获取最大长度
     */
    public static int getMaxRodCount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);

        if (data == null) {
            return DEFAULT_MAX_ROD_COUNT;
        }

        CompoundTag tag = data.copyTag();

        return tag.getInt(MAX_ROD_COUNT).orElse(DEFAULT_MAX_ROD_COUNT);
    }

    /**
     * 获取攻击距离
     */
    public static int getReachBonus(ItemStack stack) {
        return getRodCount(stack);
    }

    /**
     * 获取物品的 NBT 数据
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        return stack.getOrDefault(
                DataComponents.CUSTOM_DATA,
                CustomData.EMPTY
        ).copyTag();
    }

    /**
     * 设置当前长度
     */
    public static void setRodCount(ItemStack stack, int rodCount) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt(ROD_COUNT, rodCount);

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );

        updateAttributes(stack);
    }

    /**
     * 设置最大长度
     */
    public static void setMaxRodCount(ItemStack stack, int maxRodCount) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt(MAX_ROD_COUNT, maxRodCount);

        int current = tag.getInt(ROD_COUNT).orElse(0);
        if (current > maxRodCount) {
            tag.putInt(ROD_COUNT, maxRodCount);
        }

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );

        updateAttributes(stack);
    }

    public static void updateAttributes(ItemStack stack) {
        int rod = getReachBonus(stack); // 当前长度

        // 从物品默认属性读取，避免重复叠加
        ItemAttributeModifiers base =
                stack.getItem()
                        .components()
                        .getOrDefault(
                                DataComponents.ATTRIBUTE_MODIFIERS,
                                ItemAttributeModifiers.EMPTY
                        );

        ItemAttributeModifiers.Builder builder =
                ItemAttributeModifiers.builder();

        // 保留原始属性
        base.modifiers().forEach(entry -> {
            builder.add(
                    entry.attribute(),
                    entry.modifier(),
                    entry.slot(),
                    entry.display()
            );
        });

        // 方块交互距离
        builder.add(
                Attributes.BLOCK_INTERACTION_RANGE,
                new AttributeModifier(
                        BLOCK_REACH_ID,
                        rod,
                        AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
        );

        // 攻击速度
        builder.add(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(
                        ATTACK_SPEED_ID,
                        -0.05 * rod,
                        AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
        );

        stack.set(
                DataComponents.ATTRIBUTE_MODIFIERS,
                builder.build()
        );

        // 攻击距离
        stack.set(
                DataComponents.ATTACK_RANGE,
                new AttackRange(
                        clampRange(2.0F * rod),
                        clampRange(2.5F + 2.0F * rod),
                        clampRange(2.0F * rod),
                        clampRange(4.5F + 2.0F * rod),
                        0.125F,
                        1.0F
                )
        );

        // 更新蓄力时间
        KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
        KineticWeapon defaultKinetic = stack.getItem().components().get(DataComponents.KINETIC_WEAPON);

        if (kinetic != null && defaultKinetic != null) {
            var currentDmg = kinetic.damageConditions();
            var defaultDmg = defaultKinetic.damageConditions();

            if (currentDmg.isPresent() && defaultDmg.isPresent()) {

                // 用其他字段不变，只替换 damageConditions 的方式构造新的 KineticWeapon
                KineticWeapon modified = new KineticWeapon(
                        kinetic.contactCooldownTicks(),
                        12 + rod, // 蓄力时间
                        kinetic.dismountConditions(),
                        kinetic.knockbackConditions(),
                        kinetic.damageConditions(),
                        kinetic.forwardMovement(),
                        kinetic.damageMultiplier(),
                        kinetic.sound(),
                        kinetic.hitSound()
                );

                stack.set(DataComponents.KINETIC_WEAPON, modified);
            }
        }
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay tooltipDisplay, @NonNull Consumer<Component> tooltipAdder, @NonNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);

        int current = getRodCount(stack);
        int max = getMaxRodCount(stack);

        tooltipAdder.accept(
                Component.translatable(
                        "tooltip.hasoook.piston_spear.length",
                        current,
                        max
                ).withStyle(ChatFormatting.GRAY)
        );
    }

    private static float clampRange(float value) {
        return Math.min(value, 64.0F);
    }

    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return 72000;
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.SPEAR;
    }
}
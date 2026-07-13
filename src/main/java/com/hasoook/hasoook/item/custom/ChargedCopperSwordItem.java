package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 蓄电铜剑 — 铜质剑身，通过避雷针+铜箱子充能。
 * 攻击时释放串联闪电链，蓄电值越高闪电越粗。
 */
public class ChargedCopperSwordItem extends Item {

    public static final int CHARGE_PER_STRIKE = 10;
    private static final double CHAIN_RANGE = 8.0;
    private static final int CHAIN_DURATION_TICKS = 10;

    public ChargedCopperSwordItem(Properties properties) {
        super(properties);
    }

    // ──── 蓄电值 ────────────────────────────────────────────

    public static int getCharge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHARGE.get(), Math.max(0, charge));
    }

    public static void addCharge(ItemStack stack, int amount) {
        setCharge(stack, getCharge(stack) + amount);
    }

    public static boolean isChargedCopperSword(ItemStack stack) {
        return stack.getItem() instanceof ChargedCopperSwordItem;
    }

    // ──── 闪电链数据 ────────────────────────────────────────

    public static int getChainTicks(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), 0);
    }

    public static void setChainTicks(ItemStack stack, int ticks) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_TICKS.get(), Math.max(0, ticks));
    }

    public static Vec3 getChainPos(ItemStack stack) {
        Long encoded = stack.get(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get());
        if (encoded == null) return null;
        BlockPos bp = BlockPos.of(encoded);
        return new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
    }

    public static void setChainPos(ItemStack stack, Vec3 pos) {
        stack.set(ModDataComponents.CHARGED_COPPER_SWORD_CHAIN_POS.get(),
                BlockPos.containing(pos).asLong());
    }

    public static float getLightningWidthMultiplier(int charge) {
        return Math.min(3.0F, 1.0F + charge / 40.0F * 0.8F);
    }

    // ──── 战斗 ──────────────────────────────────────────────

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target,
                          @NonNull LivingEntity attacker) {
        int charge = getCharge(stack);
        if (charge <= 0) {
            super.hurtEnemy(stack, target, attacker);
            return;
        }
        if (!(attacker.level() instanceof ServerLevel serverLevel)) {
            super.hurtEnemy(stack, target, attacker);
            return;
        }

        float attackDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float targetExtra = charge / 20.0F;
        if (targetExtra > 0) target.hurt(target.damageSources().lightningBolt(), targetExtra);

        float chainDamage = attackDamage / 10.0F + charge / 10.0F;

        List<LivingEntity> nearby = attacker.level().getEntitiesOfClass(
                LivingEntity.class, target.getBoundingBox().inflate(CHAIN_RANGE),
                e -> e.isAlive() && e != attacker && e != target);
        for (LivingEntity e : nearby) e.hurt(e.damageSources().lightningBolt(), chainDamage);

        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);

        serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.COPPER_BREAK,
                attacker.getSoundSource(), 0.9F, 1.4F);

        spawnParticleBurst(serverLevel, targetCenter, charge);
        int idx = 0;
        for (LivingEntity next : nearby) {
            Vec3 pos = next.position().add(0, next.getBbHeight() * 0.5, 0);
            spawnParticleBurst(serverLevel, pos, charge);
            serverLevel.playSound(null, next.blockPosition(),
                    net.minecraft.sounds.SoundEvents.COPPER_BREAK,
                    attacker.getSoundSource(), 0.4F, 0.85F + idx * 0.15F);
            idx++;
        }

        setCharge(stack, charge - 1);
        setChainPos(stack, targetCenter);
        setChainTicks(stack, CHAIN_DURATION_TICKS);
        super.hurtEnemy(stack, target, attacker);
    }

    private static void spawnParticleBurst(ServerLevel level, Vec3 pos, int charge) {
        int count = 5 + charge / 10;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                pos.x, pos.y, pos.z, count, 0.3, 0.3, 0.3, 0.05);
    }

    // ──── tick ──────────────────────────────────────────────

    @Override
    public void inventoryTick(@NonNull ItemStack stack, ServerLevel level,
                              @NonNull Entity entity, @Nullable EquipmentSlot slot) {
        int remaining = getChainTicks(stack);
        if (remaining > 0) setChainTicks(stack, remaining - 1);
    }

    // ──── 提示框 ────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay tooltipDisplay, Consumer<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipComponents, tooltipFlag);
        int charge = getCharge(stack);
        if (charge > 0) {
            tooltipComponents.accept(Component.translatable(
                    "tooltip.hasoook.charged_copper_sword.charge", charge)
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.accept(Component.translatable(
                    "tooltip.hasoook.charged_copper_sword.no_charge")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

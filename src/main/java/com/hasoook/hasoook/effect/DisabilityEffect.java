package com.hasoook.hasoook.effect;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 一级伤残 — 被积木重伤后的持续负面效果。
 * <p>
 * 内置 -90% 移动速度（通过属性修饰符），无需额外施加缓慢药水。
 * 躺下和禁止跳跃由事件处理器 / Mixin 驱动。
 */
public class DisabilityEffect extends MobEffect {
    public DisabilityEffect(MobEffectCategory category, int color) {
        super(category, color);
        // 内置大幅减速：每级 -90% 移动速度（amplifier 0 时剩 10% 速度）
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Hasoook.id("effect.disability.slowness"),
                -0.9,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}

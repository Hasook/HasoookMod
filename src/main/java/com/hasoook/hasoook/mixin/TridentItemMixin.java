package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.util.TickScheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public class TridentItemMixin {
    @Inject(
            method = "releaseUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"
            )
    )
    private void hasoook$saveRiptideStartPos(
            ItemStack stack,
            Level level,
            LivingEntity living,
            int timeLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(living instanceof ServerPlayer player)) {
            return;
        }

        int undertowLevel = ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.UNDERTOW, stack);
        if (undertowLevel == 0) {
            return;
        }

        // 概率判断：1级 20%，2级 35%，3级 50%
        float chance = 0.2F + (undertowLevel - 1) * 0.15F;
        if (player.getRandom().nextFloat() >= chance) {
            return; // 未触发限流，正常激流冲刺
        }

        float strength = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        if (strength <= 0.0F) {
            return;
        }

        Vec3 startPos = new Vec3(player.getX(), player.getY(), player.getZ());

        // 随机延迟，剩余时间用来旋转，总和 20 刻
        int totalTicks = 20;
        int delayTicks = player.getRandom().nextInt(totalTicks + 1); // 0 ~ 20
        int spinTicks = totalTicks - delayTicks;

        TickScheduler.schedule(player.level(), delayTicks, () -> {
            if (!player.isRemoved()) {
                if (spinTicks > 0) {
                    player.startAutoSpinAttack(spinTicks, 0.0F, stack);
                }
                player.teleportTo(startPos.x, startPos.y, startPos.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
                player.hurtMarked = true;
            }
        });
    }
}
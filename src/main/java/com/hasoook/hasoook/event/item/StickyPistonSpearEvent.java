package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.PistonSpearItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class StickyPistonSpearEvent {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack stack = player.getWeaponItem();

            if (stack.is(ModItems.STICKY_PISTON_SPEAR.get()) && PistonSpearItem.getRodCount(stack) > 0) {
                LivingEntity target = event.getEntity();

                // 将目标拉到玩家面前
                target.teleportTo(
                        player.getX() + player.getLookAngle().x * 1.5,
                        player.getY() + player.getEyeHeight() * 0.5,
                        player.getZ() + player.getLookAngle().z * 1.5
                );
                target.setDeltaMovement(0, 0, 0);
                target.hurtMarked = true;

                player.level().playSound(player, player.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        LivingEntity attacker = target.getLastAttacker();

        // 攻击者为空时直接返回
        if (attacker == null) {
            return;
        }

        ItemStack itemStack = attacker.getWeaponItem();
        if (itemStack.isEmpty() || !itemStack.is(ModItems.STICKY_PISTON_SPEAR.get())
                || PistonSpearItem.getRodCount(itemStack) <= 0) {
            return;
        }

        // 取消击退
        event.setCanceled(true);

        PistonSpearItem.setRodCount(itemStack, 0);
    }
}

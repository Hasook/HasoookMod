package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SlimeSpearShieldMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getAttacker();

        if (!(attacker instanceof LivingEntity livingAttacker)) return;

        ItemStack weapon = livingAttacker.getMainHandStack();

        if (weapon == null || !(weapon.getItem() instanceof SlimeSpearItem)) return;

        ItemStack storedItem = SlimeSpearItem.getStoredItem(weapon);

        if (storedItem.isOf(Items.SHIELD)) {
            LivingEntity self = (LivingEntity) (Object) this;
            world.playSound(null, self.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK.value(),
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
            cir.setReturnValue(false);
        }
    }
}

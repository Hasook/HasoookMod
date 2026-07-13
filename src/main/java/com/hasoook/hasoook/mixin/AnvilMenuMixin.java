package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.item.custom.PhantomLampBlockItem;
import com.hasoook.hasoook.item.custom.SlimeSpearItem;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow
    private DataSlot cost;

    @Redirect(method = "createResultInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;supportsEnchantment(Lnet/minecraft/core/Holder;)Z"))
    private boolean hasoook$supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        // 黏液球矛
        if (stack.getItem() instanceof SlimeSpearItem) {
            ItemStack stored = SlimeSpearItem.getStoredItem(stack);
            if (!stored.isEmpty()) {
                return stored.supportsEnchantment(enchantment);
            }
        }

        // 桶
        if (stack.getItem() instanceof BucketItem) {
            if (enchantment.is(Enchantments.FIRE_PROTECTION)
                    || enchantment.is(Enchantments.MENDING)) {
                return true;
            }
        }

        return stack.supportsEnchantment(enchantment);
    }

    /**
     * Handle Phantom Lamp repair: broken lamp + 3 phantom membranes → repaired lamp
     */
    @Inject(method = "createResultInternal", at = @At("HEAD"), cancellable = true)
    private void hasoook$createPhantomLampRepair(CallbackInfo ci) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack left = self.getSlot(0).getItem();
        ItemStack right = self.getSlot(1).getItem();

        if (left.getItem() instanceof PhantomLampBlockItem && PhantomLampBlockItem.isBroken(left)) {
            if (right.is(Items.PHANTOM_MEMBRANE) && right.getCount() >= 3) {
                // Create repaired lamp as result
                ItemStack result = left.copy();
                PhantomLampBlockItem.setState(result, PhantomLampBlockItem.STATE_REPAIRED);
                self.getSlot(2).set(result);
                this.cost.set(3); // 3 XP levels
                ci.cancel(); // Skip vanilla logic for this combination
            }
        }
    }

    /**
     * Consume 3 phantom membranes when taking the repaired lamp (vanilla only consumes 1).
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void hasoook$onTakePhantomLamp(Player player, ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof PhantomLampBlockItem && PhantomLampBlockItem.isRepaired(stack)) {
            // Vanilla already consumed 1 membrane; consume 2 more for a total of 3
            AnvilMenu self = (AnvilMenu) (Object) this;
            ItemStack right = self.getSlot(1).getItem();
            if (right.is(Items.PHANTOM_MEMBRANE)) {
                int toConsume = Math.min(right.getCount(), 2);
                right.shrink(toConsume);
            }
        }
    }
}

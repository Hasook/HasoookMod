package com.hasoook.hasoook.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SolidBucketItem.class)
public abstract class SolidBucketItemMixin {

    @Unique
    private static ItemStack hasoook$originalStack = ItemStack.EMPTY;

    @Inject(
            method = "useOn",
            at = @At("HEAD")
    )
    private void hasoook$saveOriginalStack(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        hasoook$originalStack = context.getItemInHand().copy();
    }

    @Redirect(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BucketItem;getEmptySuccessItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack hasoook$useOriginalBucket(
            ItemStack ignored,
            Player player
    ) {
        return BucketItem.getEmptySuccessItem(
                hasoook$originalStack,
                player
        );
    }
}
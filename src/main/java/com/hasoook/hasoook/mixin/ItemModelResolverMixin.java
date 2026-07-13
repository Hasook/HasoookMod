package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.client.renderer.SpecialPearlRenderer;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelResolverMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void hasoook$injectPearlLayer(ItemRenderState renderState, ItemStack stack, ItemDisplayContext context, World world, HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        if (stack.isOf(ModItems.SLIME_SPEAR)) {
            SpecialPearlRenderer.attach(renderState, stack);
        }
    }
}

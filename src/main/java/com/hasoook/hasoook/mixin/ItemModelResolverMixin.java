package com.hasoook.hasoook.mixin;

import com.hasoook.hasoook.client.renderer.ArmorStandSwordRenderer;
import com.hasoook.hasoook.client.renderer.PistonSpearExtensionRenderer;
import com.hasoook.hasoook.client.renderer.SpecialPearlRenderer;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.PistonSpearItem;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemModelResolver.class})
public class ItemModelResolverMixin {
    @Inject(
            method = {"appendItemLayers"},
            at = {@At("TAIL")}
    )
    private void hasoook$injectPearlLayer(ItemStackRenderState renderState, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        if (stack.is(ModItems.SLIME_SPEAR)) {
            SpecialPearlRenderer.attach(renderState, stack);
        }

        if (stack.is(ModItems.PISTON_SPEAR)) {
            PistonSpearExtensionRenderer.attach(renderState, PistonSpearItem.getRodCount(stack), (Item)ModItems.PISTON_SPEAR_ROD.get(), (Item)ModItems.PISTON_SPEAR_HEAD.get());
        }

        if (stack.is(ModItems.STICKY_PISTON_SPEAR)) {
            PistonSpearExtensionRenderer.attach(renderState, PistonSpearItem.getRodCount(stack), (Item)ModItems.PISTON_SPEAR_ROD.get(), (Item)ModItems.STICKY_PISTON_SPEAR_HEAD.get());
        }

        if (stack.is(ModItems.ARMOR_STAND_SWORD)) {
            ArmorStandSwordRenderer.attach(renderState, stack);
        }

    }
}

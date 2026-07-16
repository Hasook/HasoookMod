package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

public class SocksCombineRecipe extends SpecialCraftingRecipe {

    public SocksCombineRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int slotCount = 0;

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                ItemStack stack = input.getStackInSlot(x, y);
                if (stack.isEmpty()) continue;

                if (!stack.isOf(ModItems.SOCK) || stack.getCount() != 1) return false;
                slotCount++;
                if (slotCount > 2) return false;
            }
        }

        return slotCount == 2;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        int wearSum = 0;
        int sockCount = 0;

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                ItemStack stack = input.getStackInSlot(x, y);
                if (stack.isEmpty()) continue;

                if (stack.isOf(ModItems.SOCK)) {
                    wearSum += stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
                    sockCount++;
                }
            }
        }

        if (sockCount != 2) return ItemStack.EMPTY;

        int avgWear = wearSum / 2;
        ItemStack result = new ItemStack(ModItems.SOCKS);
        if (avgWear > 0) {
            result.set(ModDataComponents.SOCKS_WEAR, avgWear);
        }

        return result;
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return ModRecipeSerializers.SOCKS_COMBINE;
    }
}

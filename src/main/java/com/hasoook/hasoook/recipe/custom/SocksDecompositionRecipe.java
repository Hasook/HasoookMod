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

public class SocksDecompositionRecipe extends SpecialCraftingRecipe {

    public SocksDecompositionRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int itemCount = 0;

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                ItemStack stack = input.getStackInSlot(x, y);
                if (stack.isEmpty()) continue;

                itemCount++;
                if (itemCount > 1) return false;

                if (!stack.isOf(ModItems.SOCKS)) return false;
            }
        }

        return itemCount == 1;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        ItemStack socks = ItemStack.EMPTY;

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                ItemStack stack = input.getStackInSlot(x, y);
                if (stack.isEmpty()) continue;

                if (stack.isOf(ModItems.SOCKS)) {
                    socks = stack;
                    break;
                }
            }
        }

        if (socks.isEmpty()) return ItemStack.EMPTY;

        int wear = socks.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
        ItemStack result = new ItemStack(ModItems.SOCK, 2);
        if (wear > 0) {
            result.set(ModDataComponents.SOCKS_WEAR, wear);
        }

        return result;
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return ModRecipeSerializers.SOCKS_DECOMPOSITION;
    }
}

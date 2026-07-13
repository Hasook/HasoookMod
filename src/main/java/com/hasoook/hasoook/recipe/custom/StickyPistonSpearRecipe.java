package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.PistonSpearItem;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public class StickyPistonSpearRecipe extends CustomRecipe {
    public StickyPistonSpearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        ItemStack spear = ItemStack.EMPTY;
        int stickyPistonCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.STICKY_PISTON_SPEAR.get())) {
                if (!spear.isEmpty()) {
                    return false;
                }

                spear = stack;

            } else if (stack.is(Items.PISTON)) {
                stickyPistonCount++;

            } else {
                return false;
            }
        }

        if (spear.isEmpty() || stickyPistonCount <= 0) {
            return false;
        }

        int oldMax = PistonSpearItem.getMaxRodCount(spear);
        int configMax = Config.PISTON_SPEAR_LENGTH.get();

        return oldMax + stickyPistonCount <= configMax;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        ItemStack spear = ItemStack.EMPTY;
        int stickyPistonCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.STICKY_PISTON_SPEAR.get())) {
                spear = stack;
            } else if (stack.is(Items.PISTON)) {
                stickyPistonCount++;
            }
        }

        if (spear.isEmpty() || stickyPistonCount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(ModItems.STICKY_PISTON_SPEAR.get(), 1);

        result.applyComponents(spear.getComponents());

        int newMax = PistonSpearItem.getMaxRodCount(result) + stickyPistonCount;

        if (newMax > Config.PISTON_SPEAR_LENGTH.get()) {
            return ItemStack.EMPTY;
        }

        PistonSpearItem.setMaxRodCount(result, newMax);

        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.STICKY_PISTON_SPEAR_RECIPE.get();
    }
}
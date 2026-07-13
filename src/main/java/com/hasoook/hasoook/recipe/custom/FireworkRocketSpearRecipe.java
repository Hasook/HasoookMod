package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

public class FireworkRocketSpearRecipe extends SpecialCraftingRecipe {
    public FireworkRocketSpearRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return input.getWidth() == 3
                && input.getHeight() == 3
                && input.getStackCount() == 3
                && is(input, 2, 0, Items.FIREWORK_ROCKET)
                && is(input, 1, 1, Items.STICK)
                && is(input, 0, 2, Items.STICK);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        ItemStack rocket = input.getStackInSlot(2, 0);

        FireworksComponent fireworks = rocket.get(DataComponentTypes.FIREWORKS);

        int flight = 1;
        if (fireworks != null) {
            flight = fireworks.flightDuration();
        }

        int durability = flight * 3;
        ItemStack result = new ItemStack(ModItems.FIREWORK_ROCKET_SPEAR);
        result.setDamage(Math.max(0, result.getMaxDamage() - durability));

        if (fireworks != null) {
            result.set(DataComponentTypes.FIREWORKS, fireworks);
        }

        return result;
    }

    private static boolean is(CraftingRecipeInput input, int x, int y, Item item) {
        return input.getStackInSlot(x + y * input.getWidth()).isOf(item);
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return ModRecipeSerializers.FIREWORK_ROCKET_SPEAR;
    }
}

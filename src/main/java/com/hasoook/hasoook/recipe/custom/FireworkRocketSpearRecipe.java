package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import com.hasoook.hasoook.item.ModItems;
import org.jspecify.annotations.NonNull;

public class FireworkRocketSpearRecipe extends CustomRecipe {
    public FireworkRocketSpearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        return input.width() == 3
                && input.height() == 3
                && input.ingredientCount() == 3
                && is(input, 2, 0, Items.FIREWORK_ROCKET)
                && is(input, 1, 1, Items.STICK)
                && is(input, 0, 2, Items.STICK);
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        ItemStack rocket = input.getItem(2, 0);

        // 读取烟花组件
        Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);

        int flight = 1;
        if (fireworks != null) {
            flight = fireworks.flightDuration();
        }

        // 根据飞行时间计算耐久
        int durability = flight * 3;
        ItemStack result = new ItemStack(ModItems.FIREWORK_ROCKET_SPEAR.get());
        result.setDamageValue(Math.max(0, result.getMaxDamage() - durability));

        // 复制烟花火箭组件到矛上
        if (fireworks != null) {
            result.set(DataComponents.FIREWORKS, fireworks);
        }

        return result;
    }

    private static boolean is(CraftingInput input, int x, int y, Item item) {
        return input.getItem(x, y).is(item);
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.FIREWORK_ROCKET_SPEAR.get();
    }
}
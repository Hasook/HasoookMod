package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * 合成配方：两只单只袜子 → 一双袜子，臭味等级取两只的平均值。
 */
public class SocksCombineRecipe extends CustomRecipe {

    public SocksCombineRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        int slotCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            // 每个格子必须正好是 1 只袜子，不允许堆叠，否则只消耗 1 只
            if (!stack.is(ModItems.SOCK.get()) || stack.getCount() != 1) return false;

            slotCount++;
            if (slotCount > 2) return false;
        }

        return slotCount == 2;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        int wearSum = 0;
        int sockCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SOCK.get())) {
                wearSum += stack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
                sockCount++;
            }
        }

        if (sockCount != 2) return ItemStack.EMPTY;

        int avgWear = wearSum / 2;
        ItemStack result = new ItemStack(ModItems.SOCKS.get());
        if (avgWear > 0) {
            result.set(ModDataComponents.SOCKS_WEAR.get(), avgWear);
        }

        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.SOCKS_COMBINE.get();
    }
}

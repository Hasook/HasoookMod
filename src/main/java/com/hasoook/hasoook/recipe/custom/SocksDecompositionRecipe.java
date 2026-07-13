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
 * 分解配方：一双袜子 → 两只单只袜子，继承臭味等级。
 */
public class SocksDecompositionRecipe extends CustomRecipe {

    public SocksDecompositionRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        int itemCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            itemCount++;
            if (itemCount > 1) return false;

            if (!stack.is(ModItems.SOCKS.get())) return false;
        }

        return itemCount == 1;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        ItemStack socks = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SOCKS.get())) {
                socks = stack;
                break;
            }
        }

        if (socks.isEmpty()) return ItemStack.EMPTY;

        // 创建两只单只袜子，继承磨损值
        int wear = socks.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
        ItemStack result = new ItemStack(ModItems.SOCK.get(), 2);
        if (wear > 0) {
            result.set(ModDataComponents.SOCKS_WEAR.get(), wear);
        }

        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.SOCKS_DECOMPOSITION.get();
    }
}

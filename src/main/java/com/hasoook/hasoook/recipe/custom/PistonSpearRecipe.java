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

public class PistonSpearRecipe extends CustomRecipe {
    public PistonSpearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        ItemStack spear = ItemStack.EMPTY;
        int pistonCount = 0; // 活塞计数
        int slimeCount = 0; // 黏液球计数

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.PISTON_SPEAR.get())) {
                if (!spear.isEmpty()) {
                    return false; // 只能有一个活塞矛
                }
                spear = stack;
            } else if (stack.is(Items.PISTON)) {
                pistonCount++;
            } else if (stack.is(Items.SLIME_BALL)) { // 允许黏液球
                slimeCount++;
            } else {
                return false; // 不允许其他物品
            }
        }

        // 必须有一个活塞矛，且至少有一个有效材料（活塞 或 恰好1个黏液球）
        if (spear.isEmpty() || (pistonCount <= 0 && slimeCount != 1)) {
            return false;
        }

        // 黏液球数量严格限制为0或1
        if (slimeCount > 1) {
            return false;
        }

        int oldMax = PistonSpearItem.getMaxRodCount(spear);
        int configMax = Config.PISTON_SPEAR_LENGTH.get();
        // 新最大杆数仍需符合配置
        return oldMax + pistonCount <= configMax;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        ItemStack spear = ItemStack.EMPTY;
        int pistonCount = 0; // 活塞计数
        int slimeCount = 0; // 黏液球计数

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.PISTON_SPEAR.get())) {
                spear = stack;
            } else if (stack.is(Items.PISTON)) {
                pistonCount++;
            } else if (stack.is(Items.SLIME_BALL)) {
                slimeCount++;
            }
        }

        // 必须存在矛，且至少有一个活塞或有且只有一个黏液球
        if (spear.isEmpty() || (pistonCount <= 0 && slimeCount != 1)) {
            return ItemStack.EMPTY;
        }

        int oldMax = PistonSpearItem.getMaxRodCount(spear);
        int newMax = oldMax + pistonCount;

        if (newMax > Config.PISTON_SPEAR_LENGTH.get()) {
            return ItemStack.EMPTY; // 超过配置上限则不合成
        }

        ItemStack result;
        if (slimeCount == 1) {
            // 有且只有1颗黏液球时，变为粘性活塞矛
            result = new ItemStack(ModItems.STICKY_PISTON_SPEAR.get());
        } else {
            // 复制原活塞矛
            result = spear.copyWithCount(1);
        }

        PistonSpearItem.setMaxRodCount(result, newMax);
        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.PISTON_SPEAR_RECIPE.get();
    }
}
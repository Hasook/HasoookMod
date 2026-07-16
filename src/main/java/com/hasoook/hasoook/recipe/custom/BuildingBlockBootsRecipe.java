package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * 合成配方：N个积木（堆叠也可，每格只消耗1个）+ 任意鞋子 → 原鞋子积木数量 +N（最多16个）
 */
public class BuildingBlockBootsRecipe extends CustomRecipe {

    private static final int MAX_BLOCKS = 16;

    public BuildingBlockBootsRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        int buildingBlockCount = 0;
        ItemStack bootStack = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            // 积木：每格算1个，堆叠也只消耗1个
            if (stack.is(ModItems.BUILDING_BLOCK.get())) {
                buildingBlockCount++;
                continue;
            }

            // 鞋子：只能有1双
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.slot() == EquipmentSlot.FEET) {
                if (!bootStack.isEmpty()) return false; // 不能有多双鞋子
                bootStack = stack;
                continue;
            }

            // 其他物品不允许
            return false;
        }

        if (buildingBlockCount == 0 || bootStack.isEmpty()) return false;

        // 检查是否超过上限
        int current = bootStack.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);
        return current + buildingBlockCount <= MAX_BLOCKS;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        int buildingBlockCount = 0;
        ItemStack bootStack = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.BUILDING_BLOCK.get())) {
                buildingBlockCount++;
            } else {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null && equippable.slot() == EquipmentSlot.FEET) {
                    bootStack = stack;
                }
            }
        }

        if (bootStack.isEmpty() || buildingBlockCount == 0) return ItemStack.EMPTY;

        int current = bootStack.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);
        ItemStack result = bootStack.copy();
        result.set(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), current + buildingBlockCount);
        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.BUILDING_BLOCK_BOOTS.get();
    }
}

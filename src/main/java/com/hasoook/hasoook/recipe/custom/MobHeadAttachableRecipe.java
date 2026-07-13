package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * 特殊合成配方：生物头 + 黏液球 → 可接头的生物头。
 * <p>
 * 保留原有的所有数据组件（实体类型、玩家名、UUID），
 * 并添加 {@code MOB_HEAD_ATTACHABLE = true} 标记。
 */
public class MobHeadAttachableRecipe extends CustomRecipe {

    public MobHeadAttachableRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        ItemStack head = ItemStack.EMPTY;
        boolean hasSlimeBall = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(TagKey.create(BuiltInRegistries.ITEM.key(), net.minecraft.resources.Identifier.fromNamespaceAndPath("hasoook", "mob_heads")))) {
                if (!head.isEmpty()) return false; // multiple heads
                head = stack;
            } else if (stack.is(Items.SLIME_BALL)) {
                if (hasSlimeBall) return false; // multiple slime balls
                hasSlimeBall = true;
            } else {
                return false; // unknown item
            }
        }

        return !head.isEmpty() && hasSlimeBall;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {
        ItemStack head = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(TagKey.create(BuiltInRegistries.ITEM.key(), net.minecraft.resources.Identifier.fromNamespaceAndPath("hasoook", "mob_heads")))) {
                head = stack;
                break;
            }
        }

        if (head.isEmpty()) return ItemStack.EMPTY;

        // Copy the original head, preserving all data components
        ItemStack result = head.copyWithCount(1);

        // Add the attachable flag
        result.set(ModDataComponents.MOB_HEAD_ATTACHABLE.get(), true);

        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.MOB_HEAD_ATTACHABLE.get();
    }
}

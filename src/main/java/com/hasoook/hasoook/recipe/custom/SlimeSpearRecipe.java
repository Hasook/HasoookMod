package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import static com.hasoook.hasoook.item.custom.SlimeSpearItem.updateAttributes;

public class SlimeSpearRecipe extends CustomRecipe {

    public SlimeSpearRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {

        ItemStack spear = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        int slimeBallCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModItems.SLIME_SPEAR.get())) {
                if (!spear.isEmpty()) return false; // 多把矛
                spear = stack;

            } else if (stack.is(Items.SLIME_BALL)) {
                slimeBallCount++;
                if (slimeBallCount > 1) return false; // 最多允许1颗额外黏液球

            } else {
                if (!other.isEmpty()) return false; // 多个其它物品
                other = stack;
            }
        }

        return !spear.isEmpty() && !other.isEmpty();
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider provider) {

        ItemStack other = ItemStack.EMPTY;
        boolean hasExtraSlimeBall = false;

        // 扫描输入
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(Items.SLIME_BALL)) {
                hasExtraSlimeBall = true;
                continue;
            }

            if (!stack.is(ModItems.SLIME_SPEAR.get())) {
                other = stack;
            }
        }

        ItemStack result = new ItemStack(ModItems.SLIME_SPEAR.get());

        // 同步附魔
        ItemEnchantments enchants = other.get(DataComponents.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) {
            result.set(DataComponents.ENCHANTMENTS, enchants);
        }

        int baseDurability =
                ModItems.SLIME_SPEAR.get()
                        .getDefaultInstance()
                        .getMaxDamage();

        int bonus = 0;
        boolean ultimateMaterial = false;

        // 不可损坏物品
        if (other.has(DataComponents.UNBREAKABLE)) {
            ultimateMaterial = true;
        }

        // 基岩类方块
        if (other.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().defaultDestroyTime();
            if (hardness < 0) {
                ultimateMaterial = true;
            }
        }

        if (ultimateMaterial) {
            bonus = 200;
        } else {

            // 物品耐久
            int otherMax = other.getMaxDamage();
            if (otherMax > 0) {
                bonus += Math.min(otherMax / 6, 200);
            }

            // 方块硬度
            if (other.getItem() instanceof BlockItem blockItem) {
                float hardness = blockItem.getBlock().defaultDestroyTime() * 4;
                bonus += (int) hardness;
            }

            bonus = Math.min(bonus, 200);
        }

        int finalDurability = baseDurability + bonus;

        // ✨ 额外黏液球 → 翻倍
        if (hasExtraSlimeBall) {
            finalDurability *= 2;
        }

        // （可选）上限保护
        finalDurability = Math.min(finalDurability, 2000);

        // 应用耐久
        result.set(DataComponents.MAX_DAMAGE, finalDurability);
        result.setDamageValue(0);

        // 保存附加材料ID
        String itemId = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
        CompoundTag tag = new CompoundTag();
        tag.putString("AttachedItem", itemId);

        // 标记强化状态（可用于 Tooltip / 特效）
        if (hasExtraSlimeBall) {
            tag.putBoolean("Empowered", true);
        }

        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        updateAttributes(result);

        return result;
    }

    @Override
    public @NonNull RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return ModRecipeSerializers.SLIMEBALL_SPEAR.get();
    }
}

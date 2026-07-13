package com.hasoook.hasoook.recipe.custom;

import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import static com.hasoook.hasoook.item.custom.SlimeSpearItem.updateAttributes;

public class SlimeSpearRecipe extends SpecialCraftingRecipe {

    public SlimeSpearRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {

        ItemStack spear = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        int slimeBallCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(ModItems.SLIME_SPEAR)) {
                if (!spear.isEmpty()) return false;
                spear = stack;

            } else if (stack.isOf(Items.SLIME_BALL)) {
                slimeBallCount++;
                if (slimeBallCount > 1) return false;

            } else {
                if (!other.isEmpty()) return false;
                other = stack;
            }
        }

        return !spear.isEmpty() && !other.isEmpty();
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {

        ItemStack other = ItemStack.EMPTY;
        boolean hasExtraSlimeBall = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.SLIME_BALL)) {
                hasExtraSlimeBall = true;
                continue;
            }

            if (!stack.isOf(ModItems.SLIME_SPEAR)) {
                other = stack;
            }
        }

        ItemStack result = new ItemStack(ModItems.SLIME_SPEAR);

        // Sync enchantments
        ItemEnchantmentsComponent enchants = other.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) {
            result.set(DataComponentTypes.ENCHANTMENTS, enchants);
        }

        int baseDurability = ModItems.SLIME_SPEAR.getDefaultStack().getMaxDamage();

        int bonus = 0;
        boolean ultimateMaterial = false;

        if (other.contains(DataComponentTypes.UNBREAKABLE)) {
            ultimateMaterial = true;
        }

        if (other.getItem() instanceof BlockItem blockItem) {
            float hardness = blockItem.getBlock().getHardness();
            if (hardness < 0) {
                ultimateMaterial = true;
            }
        }

        if (ultimateMaterial) {
            bonus = 200;
        } else {
            int otherMax = other.getMaxDamage();
            if (otherMax > 0) {
                bonus += Math.min(otherMax / 6, 200);
            }

            if (other.getItem() instanceof BlockItem blockItem) {
                float hardness = blockItem.getBlock().getHardness() * 4;
                bonus += (int) hardness;
            }

            bonus = Math.min(bonus, 200);
        }

        int finalDurability = baseDurability + bonus;

        if (hasExtraSlimeBall) {
            finalDurability *= 2;
        }

        finalDurability = Math.min(finalDurability, 2000);

        result.set(DataComponentTypes.MAX_DAMAGE, finalDurability);
        result.setDamage(0);

        String itemId = Registries.ITEM.getId(other.getItem()).toString();
        NbtCompound tag = new NbtCompound();
        tag.putString("AttachedItem", itemId);

        if (hasExtraSlimeBall) {
            tag.putBoolean("Empowered", true);
        }

        result.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        updateAttributes(result);

        return result;
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return ModRecipeSerializers.SLIMEBALL_SPEAR;
    }
}

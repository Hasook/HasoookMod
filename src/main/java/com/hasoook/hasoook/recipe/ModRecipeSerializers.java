package com.hasoook.hasoook.recipe;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.recipe.custom.FireworkRocketSpearRecipe;
import com.hasoook.hasoook.recipe.custom.SlimeSpearRecipe;
import com.hasoook.hasoook.recipe.custom.SocksCombineRecipe;
import com.hasoook.hasoook.recipe.custom.SocksDecompositionRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModRecipeSerializers {
    public static final RecipeSerializer<FireworkRocketSpearRecipe> FIREWORK_ROCKET_SPEAR =
            register("crafting_special_firework_rocket_spear",
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(FireworkRocketSpearRecipe::new));
    public static final RecipeSerializer<SlimeSpearRecipe> SLIMEBALL_SPEAR =
            register("crafting_special_slime_spear",
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SlimeSpearRecipe::new));
    public static final RecipeSerializer<SocksCombineRecipe> SOCKS_COMBINE =
            register("crafting_special_socks_combine",
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SocksCombineRecipe::new));
    public static final RecipeSerializer<SocksDecompositionRecipe> SOCKS_DECOMPOSITION =
            register("crafting_special_socks_decomposition",
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SocksDecompositionRecipe::new));

    private static <T extends RecipeSerializer<?>> T register(String name, T serializer) {
        return Registry.register(Registries.RECIPE_SERIALIZER, Hasoook.id(name), serializer);
    }

    public static void initialize() {
        // Called to trigger class loading
    }
}

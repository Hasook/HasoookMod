package com.hasoook.hasoook.recipe;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.recipe.custom.BuildingBlockBootsRecipe;
import com.hasoook.hasoook.recipe.custom.FireworkRocketSpearRecipe;
import com.hasoook.hasoook.recipe.custom.PistonSpearRecipe;
import com.hasoook.hasoook.recipe.custom.SlimeSpearRecipe;
import com.hasoook.hasoook.recipe.custom.MobHeadAttachableRecipe;
import com.hasoook.hasoook.recipe.custom.SocksCombineRecipe;
import com.hasoook.hasoook.recipe.custom.SocksDecompositionRecipe;
import com.hasoook.hasoook.recipe.custom.StickyPistonSpearRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Hasoook.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FireworkRocketSpearRecipe>> FIREWORK_ROCKET_SPEAR =
            SERIALIZERS.register("crafting_special_firework_rocket_spear",
                    () -> new CustomRecipe.Serializer<>(FireworkRocketSpearRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SlimeSpearRecipe>> SLIMEBALL_SPEAR =
            SERIALIZERS.register("crafting_special_slime_spear",
                    () -> new CustomRecipe.Serializer<>(SlimeSpearRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PistonSpearRecipe>> PISTON_SPEAR_RECIPE =
            SERIALIZERS.register("crafting_special_piston_spear",
                    () -> new CustomRecipe.Serializer<>(PistonSpearRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<StickyPistonSpearRecipe>> STICKY_PISTON_SPEAR_RECIPE =
            SERIALIZERS.register("crafting_special_sticky_piston_spear",
                    () -> new CustomRecipe.Serializer<>(StickyPistonSpearRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MobHeadAttachableRecipe>> MOB_HEAD_ATTACHABLE =
            SERIALIZERS.register("crafting_special_mob_head_attachable",
                    () -> new CustomRecipe.Serializer<>(MobHeadAttachableRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SocksDecompositionRecipe>> SOCKS_DECOMPOSITION =
            SERIALIZERS.register("crafting_special_socks_decomposition",
                    () -> new CustomRecipe.Serializer<>(SocksDecompositionRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SocksCombineRecipe>> SOCKS_COMBINE =
            SERIALIZERS.register("crafting_special_socks_combine",
                    () -> new CustomRecipe.Serializer<>(SocksCombineRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BuildingBlockBootsRecipe>> BUILDING_BLOCK_BOOTS =
            SERIALIZERS.register("crafting_special_building_block_boots",
                    () -> new CustomRecipe.Serializer<>(BuildingBlockBootsRecipe::new));
}

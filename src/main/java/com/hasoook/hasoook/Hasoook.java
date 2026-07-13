package com.hasoook.hasoook;

import com.hasoook.hasoook.event.item.SlimeballSpearTooltipEvents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hasoook implements ModInitializer {
	public static final String MOD_ID = "hasoook";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Custom creative tab
	public static final ItemGroup HASOOOK_TAB = Registry.register(
			Registries.ITEM_GROUP,
			id("hasoook_tab"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("creativetab.hasoook"))
					.icon(() -> new ItemStack(ModItems.SLIME_SPEAR))
					.entries((displayContext, entries) -> {
						entries.add(ModItems.SLIME_SPEAR);
						entries.add(ModItems.FIREWORK_ROCKET_SPEAR);
					})
					.build()
	);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModRecipeSerializers.initialize();

		// Register tooltip event (client-side, safe to call here)
		SlimeballSpearTooltipEvents.register();

		LOGGER.info("Hasoook initialized!");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}

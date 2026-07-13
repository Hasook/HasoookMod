package com.hasoook.hasoook;

import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.block.entity.ModBlockEntities;
import com.hasoook.hasoook.client.renderer.TransplantedHeadLayer;
import com.hasoook.hasoook.command.ModCommands;
import com.hasoook.hasoook.command.PaintQuestCommand;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.effect.ModEffects;
import com.hasoook.hasoook.enchantment.ModEnchantmentEffects;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModCreativeModeTabs;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.recipe.ModRecipeSerializers;
import com.hasoook.hasoook.screen.ModMenuTypes;
import com.hasoook.hasoook.util.TickScheduler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Hasoook.MOD_ID)
public class Hasoook {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "hasoook";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Hasoook(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModRecipeSerializers.SERIALIZERS.register(modEventBus);

        ModEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModEffects.register(modEventBus);
        ModEnchantmentEffects.register(modEventBus);
        ModAttachments.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        NeoForge.EVENT_BUS.register(TickScheduler.class);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the head transplant render layer on all living entity renderers (client-only event)
        modEventBus.addListener(this::onAddLayers);
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Hasoook) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event);
    }

    /** 为所有生物渲染器注册头部移植层。此事件仅在客户端触发。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> entityType : event.getEntityTypes()) {
            EntityRenderer<?, ?> entityRenderer = event.getRenderer(entityType);
            if (entityRenderer instanceof LivingEntityRenderer renderer) {
                renderer.addLayer(new TransplantedHeadLayer(renderer));
            }
        }
        // ★ 玩家渲染器（AvatarRenderer）不在 getEntityTypes() 中，需单独处理
        for (PlayerModelType modelType : PlayerModelType.values()) {
            AvatarRenderer<AbstractClientPlayer> playerRenderer = event.getPlayerRenderer(modelType);
            if (playerRenderer instanceof LivingEntityRenderer renderer) {
                renderer.addLayer(new TransplantedHeadLayer(renderer));
            }
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.ECHO_ARROW);
            event.accept(ModItems.SLIME_SPEAR);
            event.accept(ModItems.FIREWORK_ROCKET_SPEAR);
            event.accept(ModItems.HEAVY_SPEAR);
            event.accept(ModItems.AMETHYST_SHARD_SPEAR);
            event.accept(ModItems.END_CRYSTAL_SPEAR);
            event.accept(ModItems.NETHER_STAR_SPEAR);
            event.accept(ModItems.LIGHTNING_SPEAR);
            event.accept(ModItems.SEVOWER);
            event.accept(ModItems.ARMOR_STAND_SWORD);
            event.accept(ModItems.CHARGED_COPPER_SWORD);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.RECOVERY_CLOCK);
            event.accept(ModItems.ECHO_BOTTLE);
            event.accept(ModItems.SONIC_BOOM_BOTTLE);
            event.accept(ModItems.CHARGED_COPPER_PICKAXE);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

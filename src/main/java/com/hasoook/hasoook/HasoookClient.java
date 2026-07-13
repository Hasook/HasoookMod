package com.hasoook.hasoook;

import com.hasoook.hasoook.block.entity.ModBlockEntities;
import com.hasoook.hasoook.client.renderer.MobHeadBlockRenderer;
import com.hasoook.hasoook.client.renderer.MobHeadSpecialRenderer;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.entity.client.AmethystShardProjectileRenderer;
import com.hasoook.hasoook.entity.client.ArmorStandSwordProjectileRenderer;
import com.hasoook.hasoook.entity.client.EchoArrowRenderer;
import com.hasoook.hasoook.entity.client.HeavyHalberdProjectileRenderer;
import com.hasoook.hasoook.entity.client.SevowerProjectileRenderer;
import com.hasoook.hasoook.screen.ModMenuTypes;
import com.hasoook.hasoook.screen.custom.RedEnvelopeScreen;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Hasoook.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class HasoookClient {
    public HasoookClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntities.AMETHYST_SHARD.get(), AmethystShardProjectileRenderer::new);
        EntityRenderers.register(ModEntities.SEVOWER.get(), SevowerProjectileRenderer::new);
        EntityRenderers.register(ModEntities.ECHO_ARROW.get(), EchoArrowRenderer::new);
        EntityRenderers.register(ModEntities.HEAVY_HALBERD.get(), HeavyHalberdProjectileRenderer::new);
        EntityRenderers.register(ModEntities.ARMOR_STAND_SWORD_PROJECTILE.get(), ArmorStandSwordProjectileRenderer::new);
        EntityRenderers.register(ModEntities.THROWN_SOCK.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RED_ENVELOPE_MENU.get(), RedEnvelopeScreen::new);
    }

    @SubscribeEvent
    static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Hasoook.id("mob_head"), MobHeadSpecialRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Hasoook.LOGGER.info("[HasoookClient] registerRenderers event fired! Registering MobHead BER...");
        event.registerBlockEntityRenderer(ModBlockEntities.MOB_HEAD.get(),
                ctx -> new MobHeadBlockRenderer());
        Hasoook.LOGGER.info("[HasoookClient] MobHead BER registered for type: {}",
                ModBlockEntities.MOB_HEAD.getId());
    }
}

package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.client.renderer.ThirdPersonMapLayer;
import com.hasoook.hasoook.screen.custom.PaintScreen;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class PaperAndQuillClientEvent {
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() == ModItems.MAGIC_PAINTBRUSH_AND_PAPER.get() && event.getLevel().isClientSide()) {
            Minecraft.getInstance().setScreen(new PaintScreen());

            event.getLevel().playSound(
                    event.getEntity(),
                    event.getPos(),
                    SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /**
     * 展示型地图：渲染出其他玩家手持的地图内容
     */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        AvatarRenderer<AbstractClientPlayer> wide = event.getPlayerRenderer(PlayerModelType.WIDE);

        if (wide != null) {
            wide.addLayer(new ThirdPersonMapLayer(wide));
        }

        AvatarRenderer<AbstractClientPlayer> slim = event.getPlayerRenderer(PlayerModelType.SLIM);

        if (slim != null) {
            slim.addLayer(new ThirdPersonMapLayer(slim));
        }
    }
}
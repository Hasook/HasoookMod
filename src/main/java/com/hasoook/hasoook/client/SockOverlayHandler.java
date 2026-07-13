package com.hasoook.hasoook.client;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.network.payload.RemoveSockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Random;

@EventBusSubscriber(value = Dist.CLIENT, modid = Hasoook.MOD_ID)
public class SockOverlayHandler {

    private static final Identifier[] TEXTURES = {
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay.png"),
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay_1.png"),
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/misc/sock_overlay_2.png"),
    };

    private static final int ICON_SIZE = 16;
    private static final int TEX_SIZE = 256;
    private static final int DROP_ZONE_HEIGHT = 32;

    // 拖拽状态
    private static int draggingIndex = -1;
    private static int removedPacked = -1; // 已移除但服务端还没确认的 packed 值
    private static int dragMouseX, dragMouseY;

    // ── packed 字段提取 ──
    private static int remaining(int p) { return p & 0xFFF; }          // 12 bits
    private static int seed(int p)     { return (p >> 12) & 0xF; }    // 4 bits
    private static int wearStage(int p) { return (p >> 16) & 0xFF; }
    private static int texIndex(int p)  { return (p >> 24) & 0xFF; }

    // ── 第一人称遮罩 ──

    @SubscribeEvent
    public static void onPostRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        String data = player.getData(ModAttachments.SOCK_FACE.get());
        if (data.isEmpty()) return;

        GuiGraphics gfx = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        for (String part : data.split(",")) {
            int packed = Integer.parseInt(part);
            int rem = remaining(packed);
            if (rem <= 0) continue;

            int ti = texIndex(packed);
            if (ti >= TEXTURES.length) ti = 0;

            float alpha = Math.min(rem / 20f, 1.0f);
            int tint = ((int) (alpha * 255) << 24) | 0x00FFFFFF;

            Random rand = new Random(seed(packed) * 7919L + ti * 6271L + wearStage(packed) * 13337L);
            int jx = rand.nextInt(w * 3 / 4) - w * 3 / 8;
            int jy = rand.nextInt(h * 3 / 4) - h * 3 / 8;
            int x = (w - TEX_SIZE) / 2 + jx;
            int y = (h - TEX_SIZE) / 2 + jy;

            gfx.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[ti],
                    x, y, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE, tint);
        }
    }

    // ══════════════════════════════════════
    //  背包 GUI — 拖拽到下方区域摘除
    // ══════════════════════════════════════

    private static void screenMouse(Minecraft mc, AbstractContainerScreen<?> screen, int[] out) {
        int scrW = screen.width;
        int scrH = screen.height;
        out[0] = (int) (mc.mouseHandler.xpos() * (double) scrW / mc.getWindow().getWidth());
        out[1] = (int) (mc.mouseHandler.ypos() * (double) scrH / mc.getWindow().getHeight());
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = screen.getMinecraft();
        LocalPlayer player = mc.player;
        if (player == null) return;

        String data = player.getData(ModAttachments.SOCK_FACE.get());
        // 服务端已确认移除（列表中不再有该 packed 值）→ 清除标记
        if (removedPacked >= 0 && !data.contains(String.valueOf(removedPacked))) {
            removedPacked = -1;
        }
        if (data.isEmpty() && draggingIndex < 0 && removedPacked < 0) return;

        GuiGraphics gfx = event.getGuiGraphics();
        int scrW = screen.width;
        int scrH = screen.height;
        int[] m = new int[2];
        screenMouse(mc, screen, m);
        int mouseX = m[0], mouseY = m[1];

        String[] parts = data.isEmpty() ? new String[0] : data.split(",");

        // ── 绘制袜子图标（跳过拖拽中和已移除的）──
        for (int i = 0; i < parts.length; i++) {
            int packed = Integer.parseInt(parts[i]);
            if (i == draggingIndex || packed == removedPacked) continue;
            if (remaining(packed) <= 0) continue;

            int ti = texIndex(packed);
            Random rand = new Random(seed(packed) * 7919L + ti * 6271L + wearStage(packed) * 13337L);
            int iconX = rand.nextInt(scrW - ICON_SIZE);
            int iconY = rand.nextInt(scrH - ICON_SIZE);

            ItemStack stack = new ItemStack(ModItems.SOCK.get());
            gfx.renderItem(stack, iconX, iconY);

            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                gfx.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x66FF0000);
            }
        }

        // ── 底部摘除区域（仅拖拽时显示）──
        if (draggingIndex >= 0) {
            int zoneX = scrW / 2 - 80;
            int zoneY = scrH - DROP_ZONE_HEIGHT - 4;
            int zoneW = 160;
            int zoneH = DROP_ZONE_HEIGHT;

            boolean hoveringZone = mouseX >= zoneX && mouseX <= zoneX + zoneW
                    && mouseY >= zoneY && mouseY <= zoneY + zoneH;
            int zoneColor = hoveringZone ? 0xCC553322 : 0x88332211;
            gfx.fill(zoneX, zoneY, zoneX + zoneW, zoneY + zoneH, zoneColor);
            gfx.drawCenteredString(mc.font,
                    Component.translatable("gui.hasoook.sock_drop_zone"),
                    scrW / 2, zoneY + 10, 0xFFAAAAAA);
        }

        // ── 绘制正被拖拽的袜子（跟随鼠标）──
        if (draggingIndex >= 0 && draggingIndex < parts.length) {
            int packed = Integer.parseInt(parts[draggingIndex]);
            if (remaining(packed) > 0) {
                ItemStack stack = new ItemStack(ModItems.SOCK.get());
                gfx.renderItem(stack, mouseX - ICON_SIZE / 2, mouseY - ICON_SIZE / 2);
            } else {
                draggingIndex = -1;
            }
        }
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Minecraft mc = screen.getMinecraft();
        LocalPlayer player = mc.player;
        if (player == null) return;

        String data = player.getData(ModAttachments.SOCK_FACE.get());
        if (data.isEmpty()) return;

        int[] m = new int[2];
        screenMouse(mc, screen, m);
        int mouseX = m[0], mouseY = m[1];

        String[] parts = data.split(",");
        for (int i = 0; i < parts.length; i++) {
            int packed = Integer.parseInt(parts[i]);
            if (remaining(packed) <= 0) continue;

            int ti = texIndex(packed);
            Random rand = new Random(seed(packed) * 7919L + ti * 6271L + wearStage(packed) * 13337L);
            int iconX = rand.nextInt(screen.width - ICON_SIZE);
            int iconY = rand.nextInt(screen.height - ICON_SIZE);

            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                draggingIndex = i;
                dragMouseX = mouseX;
                dragMouseY = mouseY;
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (draggingIndex < 0) return;

        Minecraft mc = screen.getMinecraft();
        LocalPlayer player = mc.player;
        if (player == null) { draggingIndex = -1; return; }

        int[] m = new int[2];
        screenMouse(mc, screen, m);
        int mouseX = m[0], mouseY = m[1];

        int scrW = screen.width;
        int scrH = screen.height;
        int zoneX = scrW / 2 - 80;
        int zoneY = scrH - DROP_ZONE_HEIGHT - 4;
        int zoneW = 160;
        int zoneH = DROP_ZONE_HEIGHT;

        // 释放到摘除区域 → 移除（乐观隐藏，防止闪现）
        if (mouseX >= zoneX && mouseX <= zoneX + zoneW
                && mouseY >= zoneY && mouseY <= zoneY + zoneH) {
            String curData = player.getData(ModAttachments.SOCK_FACE.get());
            String[] curParts = curData.isEmpty() ? new String[0] : curData.split(",");
            if (draggingIndex >= 0 && draggingIndex < curParts.length) {
                removedPacked = Integer.parseInt(curParts[draggingIndex]);
            }
            ClientPacketDistributor.sendToServer(new RemoveSockPayload(draggingIndex));
        }

        draggingIndex = -1;
        event.setCanceled(true);
    }
}

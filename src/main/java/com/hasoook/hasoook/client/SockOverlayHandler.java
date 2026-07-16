package com.hasoook.hasoook.client;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.SockFaceData;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.network.payload.RemoveSockPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Random;

public class SockOverlayHandler {

    private static final Identifier[] TEXTURES = {
            Hasoook.id("textures/misc/sock_overlay.png"),
            Hasoook.id("textures/misc/sock_overlay_1.png"),
            Hasoook.id("textures/misc/sock_overlay_2.png"),
    };

    private static final int ICON_SIZE = 16;
    private static final int TEX_SIZE = 256;
    private static final int DROP_ZONE_HEIGHT = 32;

    private static int draggingIndex = -1;
    private static int removedPacked = -1;

    private static int remaining(int p) { return p & 0xFFF; }
    private static int seed(int p) { return (p >> 12) & 0xF; }
    private static int wearStage(int p) { return (p >> 16) & 0xFF; }
    private static int texIndex(int p) { return (p >> 24) & 0xFF; }

    public static void register() {
        // ── 第一人称 HUD 遮罩 ──
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientPlayerEntity player = mc.player;
            if (player == null) return;
            if (!mc.options.getPerspective().isFirstPerson()) return;

            String data = SockFaceData.getSockFace(player);
            if (data.isEmpty()) return;

            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();

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

                drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURES[ti],
                        x, y, 0, 0, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE, tint);
            }
        });

        // ── 背包 GUI 事件 ──
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?>) {
                registerScreenEvents(screen);
            }
        });
    }

    private static void registerScreenEvents(Screen screen) {
        ScreenEvents.afterRender(screen).register((scr, drawContext, mouseX, mouseY, tickDelta) -> {
            onScreenRender(scr, drawContext, mouseX, mouseY);
        });

        ScreenMouseEvents.beforeMouseClick(screen).register((scr, context) -> {
            onMousePress(scr, (int) context.x(), (int) context.y());
        });

        ScreenMouseEvents.beforeMouseRelease(screen).register((scr, context) -> {
            onMouseRelease(scr, (int) context.x(), (int) context.y());
        });
    }

    private static void onScreenRender(Screen screen, DrawContext drawContext, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        String data = SockFaceData.getSockFace(player);
        if (removedPacked >= 0 && !data.contains(String.valueOf(removedPacked))) {
            removedPacked = -1;
        }
        if (data.isEmpty() && draggingIndex < 0 && removedPacked < 0) return;

        int scrW = screen.width;
        int scrH = screen.height;

        String[] parts = data.isEmpty() ? new String[0] : data.split(",");

        for (int i = 0; i < parts.length; i++) {
            int packed = Integer.parseInt(parts[i]);
            if (i == draggingIndex || packed == removedPacked) continue;
            if (remaining(packed) <= 0) continue;

            int ti = texIndex(packed);
            Random rand = new Random(seed(packed) * 7919L + ti * 6271L + wearStage(packed) * 13337L);
            int iconX = rand.nextInt(scrW - ICON_SIZE);
            int iconY = rand.nextInt(scrH - ICON_SIZE);

            ItemStack stack = new ItemStack(ModItems.SOCK);
            drawContext.drawItem(stack, iconX, iconY);

            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                    && mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                drawContext.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x66FF0000);
            }
        }

        if (draggingIndex >= 0) {
            int zoneX = scrW / 2 - 80;
            int zoneY = scrH - DROP_ZONE_HEIGHT - 4;
            int zoneW = 160;
            int zoneH = DROP_ZONE_HEIGHT;

            boolean hoveringZone = mouseX >= zoneX && mouseX <= zoneX + zoneW
                    && mouseY >= zoneY && mouseY <= zoneY + zoneH;
            int zoneColor = hoveringZone ? 0xCC553322 : 0x88332211;
            drawContext.fill(zoneX, zoneY, zoneX + zoneW, zoneY + zoneH, zoneColor);
            drawContext.drawCenteredTextWithShadow(mc.textRenderer,
                    Text.translatable("gui.hasoook.sock_drop_zone"),
                    scrW / 2, zoneY + 10, 0xFFAAAAAA);
        }

        if (draggingIndex >= 0 && draggingIndex < parts.length) {
            int packed = Integer.parseInt(parts[draggingIndex]);
            if (remaining(packed) > 0) {
                ItemStack stack = new ItemStack(ModItems.SOCK);
                drawContext.drawItem(stack, mouseX - ICON_SIZE / 2, mouseY - ICON_SIZE / 2);
            } else {
                draggingIndex = -1;
            }
        }
    }

    private static void onMousePress(Screen screen, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        String data = SockFaceData.getSockFace(player);
        if (data.isEmpty()) return;

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
                return;
            }
        }
    }

    private static void onMouseRelease(Screen screen, int mouseX, int mouseY) {
        if (draggingIndex < 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) { draggingIndex = -1; return; }

        int scrW = screen.width;
        int scrH = screen.height;
        int zoneX = scrW / 2 - 80;
        int zoneY = scrH - DROP_ZONE_HEIGHT - 4;
        int zoneW = 160;
        int zoneH = DROP_ZONE_HEIGHT;

        if (mouseX >= zoneX && mouseX <= zoneX + zoneW
                && mouseY >= zoneY && mouseY <= zoneY + zoneH) {
            String curData = SockFaceData.getSockFace(player);
            String[] curParts = curData.isEmpty() ? new String[0] : curData.split(",");
            if (draggingIndex >= 0 && draggingIndex < curParts.length) {
                removedPacked = Integer.parseInt(curParts[draggingIndex]);
            }
            ClientPlayNetworking.send(new RemoveSockPayload(draggingIndex));
        }

        draggingIndex = -1;
    }
}

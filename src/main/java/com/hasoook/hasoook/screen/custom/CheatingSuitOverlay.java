package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.item.custom.PokerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;

/**
 * 出千附魔 HUD：物品栏上方显示4个花色，连续同花色高亮表示会齐射
 */
public class CheatingSuitOverlay {

    private static final int CARD_W = 16;
    private static final int CARD_H = 22;
    private static final int GAP = 2;

    @SubscribeEvent
    public static void onRenderGui(Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof PokerItem)) return;
        if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CHEATING, stack) <= 0) return;
        if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CARD_THROW, stack) <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        int[] suits = PokerItem.getSuits(stack);

        // 统计连续同花色数量 (从左端开始)
        int first = suits[0];
        int chain = 1;
        for (int i = 1; i < 4; i++) {
            if (suits[i] == first) chain++; else break;
        }

        // 链式样色
        int chainBorder = switch (chain) {
            case 2 -> 0xFFFFD700; // 金色 对子
            case 3 -> 0xFFCC66FF; // 紫色 三连
            case 4 -> 0xFFFF3333; // 红色 炸弹
            default -> 0xFFFFD700;
        };
        String hint = switch (chain) {
            case 2 -> "对子"; case 3 -> "三连"; case 4 -> "炸弹"; default -> "";
        };
        int hintColor = chainBorder;
        String prefix = switch (chain) {
            case 2 -> "§6§l"; case 3 -> "§d§l"; case 4 -> "§c§l"; default -> "§f§l";
        };

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int totalW = 4 * CARD_W + 3 * GAP;
        int x = (screenW - totalW) / 2;
        int y = screenH - 22 - CARD_H - 28; // 物品栏上方 28px, 避免与物品名重叠

        for (int i = 0; i < 4; i++) {
            int cx = x + i * (CARD_W + GAP);
            int suit = suits[i];
            int color = PokerItem.suitColor(suit);
            String symbol = PokerItem.suitSymbol(suit);

            g.fill(cx, y, cx + CARD_W, y + CARD_H, 0xEEFCFCF8);
            g.renderOutline(cx, y, CARD_W, CARD_H, 0xFF888888);
            g.drawCenteredString(mc.font, symbol, cx + CARD_W / 2, y + 2, color);

            if (i < chain) {
                g.renderOutline(cx - 1, y - 1, CARD_W + 2, CARD_H + 2, chainBorder);
            }
        }

        // 斗地主术语 — 显示在连锁的牌中间
        if (chain > 1) {
            int chainW = chain * CARD_W + (chain - 1) * GAP;
            int hintW = mc.font.width(hint);
            int hintX = x + (chainW - hintW) / 2;
            int hintY = y + CARD_H - 11;
            g.drawString(mc.font, prefix + hint, hintX, hintY, hintColor);
        }
    }
}

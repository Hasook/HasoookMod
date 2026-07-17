package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.network.payload.BlackjackActionPayload;
import com.hasoook.hasoook.network.payload.BlackjackSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class BlackjackGameScreen extends AbstractContainerScreen<BlackjackGameMenu> {

    private static final int CARD_W = 46, CARD_H = 46, CARD_GAP = 6;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55, GOLD = 0xFFFFD700, RED = 0xFFFF5555;
    private static final int DEALER_Y = 40, CARD_D_Y = 50, D_SCORE_Y = 100, SEP_Y = 112;
    private static final int PLAYER_Y = 122, CARD_P_Y = 132, P_SCORE_Y = 182;
    private static final int RESULT_Y = 196, BTN_Y = 214;

    BlackjackSyncPayload gameState = new BlackjackSyncPayload(
            0, new ArrayList<>(), new ArrayList<>(), true, 0, 0, 0, 0, "", false, 0, false, 0, false);

    private Button btn2x, btn4x, btn8x, btnExit;
    private Button btnHit, btnStand, btnSurrender, btnDouble, btnClose;
    private Button btnDoudizhu; // 斗地主邀请按钮
    private boolean readySent = false;

    // ── 出千拖拽 ──
    private int dragCardIdx = -1;      // 正在拖的牌索引
    private float dragDist;            // 累计晃动距离
    private double lastMx, lastMy;     // 上一帧鼠标位置

    public BlackjackGameScreen(BlackjackGameMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;
        this.imageHeight = 250;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    public void updateGameState(BlackjackSyncPayload s) { gameState = s; rebuildButtons(); }

    @Override protected void init() {
        super.init(); rebuildButtons();
        if (!readySent) { send(BlackjackGameMenu.ACTION_SCREEN_READY, 0); readySent = true; }
    }

    private void rebuildButtons() {
        clearWidgets();
        int cx = guiLeft(), cy = guiTop();
        switch (gameState.phase()) {
            case BlackjackGameMenu.PHASE_MULTIPLIER -> {
                int bw = 55, gap = 8, total = bw * 4 + gap * 3;
                int bx = cx + (imageWidth - total) / 2;
                btn2x  = btn("×1", 0, 1, bx, cy + 105, bw);
                btn4x  = btn("×2", 0, 2, bx + bw + gap, cy + 105, bw);
                btn8x  = btn("×4", 0, 4, bx + (bw+gap)*2, cy + 105, bw);
                var bx8 = btn("×8", 0, 8, bx + (bw+gap)*3, cy + 105, bw);
                btnExit = Button.builder(Component.literal("退出"), b -> onClose())
                        .bounds(cx + imageWidth/2 - 30, cy + 135, 60, 20).build();
                // 斗地主邀请按钮
                btnDoudizhu = Button.builder(Component.literal("🃏 邀请斗地主"),
                                b -> send(BlackjackGameMenu.ACTION_INVITE_DOUDIZHU, 0))
                        .bounds(cx + imageWidth/2 - 50, cy + 158, 100, 20).build();
                addRenderableWidget(btn2x); addRenderableWidget(btn4x);
                addRenderableWidget(btn8x); addRenderableWidget(bx8);
                addRenderableWidget(btnExit);
                addRenderableWidget(btnDoudizhu);
            }
            case BlackjackGameMenu.PHASE_PLAYING -> {
                int bw = 54, total = bw * 4 + 8 * 3, bx = cx + (imageWidth - total) / 2;
                btnHit   = btn("要牌", 1, 0, bx, cy + BTN_Y, bw);
                btnStand = btn("停牌", 2, 0, bx + bw + 8, cy + BTN_Y, bw);
                btnSurrender = btn("认输", 3, 0, bx + (bw+8)*2, cy + BTN_Y, bw);
                btnDouble    = btn("加倍", 5, 0, bx + (bw+8)*3, cy + BTN_Y, bw);
                addRenderableWidget(btnHit); addRenderableWidget(btnStand);
                addRenderableWidget(btnSurrender); addRenderableWidget(btnDouble);
                btnDouble.active = gameState.canDoubleDown() && !gameState.dealerDrawing();
                if (gameState.dealerDrawing()) {
                    btnHit.active = false; btnStand.active = false;
                    btnSurrender.active = false;
                }
            }
            case BlackjackGameMenu.PHASE_FINISHED -> {
                btnClose = Button.builder(Component.literal("关闭"), b -> onClose())
                        .bounds(cx + imageWidth/2 - 30, cy + BTN_Y, 60, 20).build();
                addRenderableWidget(btnClose);
            }
        }
    }

    private Button btn(String text, int action, int data, int x, int y, int w) {
        return Button.builder(Component.literal(text), b -> send(action, data))
                .bounds(x, y, w, 20).build();
    }

    private void send(int a, int d) {
        if (Minecraft.getInstance().getConnection() != null)
            Minecraft.getInstance().getConnection().send(new BlackjackActionPayload(a, d));
    }

    // ═══════════════════════════════════════════════════════════════
    // 鼠标：出千拖拽
    // ═══════════════════════════════════════════════════════════════

    private boolean hasCheatEnchant() {
        if (minecraft == null || minecraft.player == null) return false;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack held = minecraft.player.getItemInHand(hand);
            if (held.getItem() instanceof com.hasoook.hasoook.item.custom.PokerItem
                    && held.getEnchantments().keySet().stream()
                    .anyMatch(h -> h.getRegisteredName().contains("cheating")))
                return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (!dbl && e.button() == 0 && gameState.phase() == BlackjackGameMenu.PHASE_PLAYING
                && !gameState.dealerDrawing() && hasCheatEnchant()) {
            int idx = cardAt(e.x(), e.y());
            if (idx == 0 && idx < gameState.playerCards().size()) { // 只能抓第一张
                dragCardIdx = idx;
                dragDist = 0;
                lastMx = e.x(); lastMy = e.y();
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragCardIdx >= 0 && e.button() == 0) {
            if (dragDist > 30) send(BlackjackGameMenu.ACTION_CHEAT, 0);
            dragCardIdx = -1;
            return true;
        }
        return super.mouseReleased(e);
    }

    /// 检测鼠标在哪张玩家牌上
    private int cardAt(double mx, double my) {
        int cx = guiLeft(), cy = guiTop();
        List<Integer> pc = gameState.playerCards();
        int px = cardX(pc.size(), cx + imageWidth / 2);
        for (int i = 0; i < pc.size(); i++) {
            int x = px + i * (CARD_W + CARD_GAP);
            int y = cy + CARD_P_Y;
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H)
                return i;
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════════════════════

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int cx = guiLeft(), cy = guiTop(), mid = cx + imageWidth / 2;

        draw(g, "21点 - Blackjack", cx + 8, cy + 6, GOLD);
        int em = countEmeralds(), mult = gameState.multiplier(), bs = gameState.baseStake();
        int vb = gameState.villagerBudget();
        if (vb >= 0)
            draw(g, "村民: " + vb + "  底注 " + bs, cx + 8, cy + 18, 0xFFCCAA55);
        drawR(g, "绿宝石: " + em, cx + imageWidth - 8, cy + 6, GREEN);
        if (mult > 0)
            drawR(g, "倍率 x" + mult + "  押 " + (bs * mult), cx + imageWidth - 8, cy + 18, GOLD);

        switch (gameState.phase()) {
            case BlackjackGameMenu.PHASE_MULTIPLIER -> rendMulti(g, cx, cy, mid);
            case BlackjackGameMenu.PHASE_PLAYING    -> rendPlay(g, cx, cy, mid);
            case BlackjackGameMenu.PHASE_FINISHED   -> rendFinish(g, cx, cy, mid);
        }

        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);

        // 拖拽中的牌（画在最上层）并追踪晃动距离
        if (dragCardIdx >= 0 && dragCardIdx < gameState.playerCards().size()) {
            int card = gameState.playerCards().get(dragCardIdx);
            card(g, card, (int)mx - CARD_W/2, (int)my - CARD_H/2);
            dragDist += (float)(Math.abs(mx - lastMx) + Math.abs(my - lastMy));
            lastMx = mx; lastMy = my;
        }
    }

    private void rendMulti(GuiGraphics g, int cx, int cy, int mid) {
        int bs = gameState.baseStake();
        if (!gameState.message().isEmpty()) drawC(g, gameState.message(), mid, cy + 30, RED);
        drawC(g, "底注 " + bs + " 绿宝石", mid, cy + 55, GOLD);
        drawC(g, "请选择倍率", mid, cy + 75, GRAY);
    }

    private void rendPlay(GuiGraphics g, int cx, int cy, int mid) {
        boolean isDealer = gameState.playerIsDealer();
        String dealerLabel = isDealer ? "村民" : "庄家";

        drawC(g, dealerLabel, mid, cy + DEALER_Y, GRAY);
        List<Integer> dc = gameState.dealerCards();
        int dx = cardX(dc.size(), mid);
        for (int i = 0; i < dc.size(); i++) {
            if (i == 0 && gameState.dealerHidden()) hidden(g, dx + i * (CARD_W + CARD_GAP), cy + CARD_D_Y);
            else card(g, dc.get(i), dx + i * (CARD_W + CARD_GAP), cy + CARD_D_Y);
        }
        drawC(g, gameState.dealerHidden() && dc.size() > 1 ? "? + " + visibleScore(dc) : "" + gameState.dealerScore(),
                mid, cy + D_SCORE_Y, gameState.dealerHidden() ? GRAY : GOLD);
        drawC(g, "──────────────────────", mid, cy + SEP_Y, 0xFF555555);
        drawC(g, "你", mid, cy + PLAYER_Y, GREEN);

        List<Integer> pc = gameState.playerCards();
        int px = cardX(pc.size(), mid);
        for (int i = 0; i < pc.size(); i++) {
            if (i == dragCardIdx) {
                // 拖拽中的牌半透明在原位
                cardGhost(g, pc.get(i), px + i * (CARD_W + CARD_GAP), cy + CARD_P_Y);
            } else {
                card(g, pc.get(i), px + i * (CARD_W + CARD_GAP), cy + CARD_P_Y);
            }
        }
        drawC(g, "点数: " + gameState.playerScore(), mid, cy + P_SCORE_Y, GOLD);

        if (!gameState.message().isEmpty()) drawC(g, gameState.message(), mid, cy + DEALER_Y - 10, GOLD);

    }

    private void rendFinish(GuiGraphics g, int cx, int cy, int mid) {
        boolean isDealer = gameState.playerIsDealer();
        drawC(g, isDealer ? "村民" : "庄家", mid, cy + DEALER_Y, GRAY);
        List<Integer> dc = gameState.dealerCards();
        int dx = cardX(dc.size(), mid);
        for (int i = 0; i < dc.size(); i++) card(g, dc.get(i), dx + i * (CARD_W + CARD_GAP), cy + CARD_D_Y);
        drawC(g, "点数: " + gameState.dealerScore(), mid, cy + D_SCORE_Y, GOLD);
        drawC(g, "──────────────────────", mid, cy + SEP_Y, 0xFF555555);
        drawC(g, "你", mid, cy + PLAYER_Y, GREEN);
        List<Integer> pc = gameState.playerCards();
        int px = cardX(pc.size(), mid);
        for (int i = 0; i < pc.size(); i++) card(g, pc.get(i), px + i * (CARD_W + CARD_GAP), cy + CARD_P_Y);
        drawC(g, "点数: " + gameState.playerScore(), mid, cy + P_SCORE_Y, GOLD);
        int rc = switch (gameState.result()) {
            case BlackjackGameMenu.RESULT_WIN  -> GREEN;
            case BlackjackGameMenu.RESULT_LOSE -> RED;
            default -> GOLD;
        };
        drawC(g, gameState.message(), mid, cy + RESULT_Y, rc);
    }

    // ═══════════════════════════════════════════════════════════════
    // 卡牌
    // ═══════════════════════════════════════════════════════════════

    private void card(GuiGraphics g, int c, int x, int y) {
        int suit = c / 13, rank = c % 13;
        boolean r = (suit == 1 || suit == 2);
        int col = r ? 0xFFFF3333 : 0xFF222222;
        String[] S = {"♠","♥","♦","♣"}, R = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        g.fill(x, y, x + CARD_W, y + CARD_H, 0xFFF8F8F0);
        g.renderOutline(x, y, CARD_W, CARD_H, 0xFF666666);
        draw(g, R[rank], x + 4, y + 3, col);
        draw(g, S[suit], x + 4, y + 14, col);
        drawC(g, S[suit], x + CARD_W/2, y + 13, col);
    }

    private void cardGhost(GuiGraphics g, int c, int x, int y) {
        // 半透明原位标记
        g.fill(x, y, x + CARD_W, y + CARD_H, 0x66F8F8F0);
        g.renderOutline(x, y, CARD_W, CARD_H, 0x66888888);
    }

    private void hidden(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF4A4ABE);
        g.renderOutline(x, y, CARD_W, CARD_H, 0xFF8888DD);
        drawC(g, "?", x + CARD_W/2, y + 13, 0xFFBBBBEE);
    }

    private static int cardX(int n, int c) { return c - (n * CARD_W + (n-1) * CARD_GAP) / 2; }

    private static int visibleScore(List<Integer> cards) {
        int s = 0;
        for (int i = 1; i < cards.size(); i++) {
            int r = cards.get(i) % 13;
            s += (r == 0 ? 11 : r >= 10 ? 10 : r + 1);
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════════

    private void draw(GuiGraphics g, String s, int x, int y, int c) { g.drawString(font, s, x, y, c); }
    private void drawC(GuiGraphics g, String s, int cx, int y, int c) { g.drawCenteredString(font, s, cx, y, c); }
    private void drawR(GuiGraphics g, String s, int r, int y, int c) { g.drawString(font, s, r - font.width(s), y, c); }

    private int countEmeralds() {
        if (minecraft == null || minecraft.player == null) return 0;
        int n = 0; Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Items.EMERALD)) n += inv.getItem(i).getCount();
        return n;
    }

    private int guiLeft() { return (width - imageWidth) / 2; }
    private int guiTop()  { return (height - imageHeight) / 2; }
}

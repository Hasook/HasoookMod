package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.network.payload.DouDiZhuActionPayload;
import com.hasoook.hasoook.network.payload.DouDiZhuSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * 斗地主游戏画面 — 无背景紧凑布局 (460×254)
 * <pre>
 * Y=8   标题: ◆斗地主 [L] | 底牌文字/阶段名 [C] | ◆绿宝石 [R]
 * Y=22  信息: 底注:X  ×X  叫分:X
 * Y=36  村民名: [v1👑◀] [L]          [v2👑◀] [R]
 * Y=50  村民信息: 牌×17 资:XX [L]    牌×17 资:XX [R]
 * Y=62  村民出牌/不出 (各自位置, 持久显示)
 * Y=102 消息 [C] (村民不出不在此显示)
 * Y=112 玩家出牌标签: "你 → 牌型" [C]
 * Y=122 玩家出牌小卡 [C] (34px → 156)
 * Y=162 按钮区 (20px → 182)
 * Y=186 手牌标签
 * Y=196 手牌大卡 (54px → 250)
 * └───── 254 ─────┘
 * </pre>
 */
public class DouDiZhuGameScreen extends AbstractContainerScreen<DouDiZhuGameMenu> {

    private static final int CARD_W = 38, CARD_H = 54;
    private static final int SMALL_W = 24, SMALL_H = 34;
    private static final int MIN_OV = 6, MAX_OV = 24;
    private static final int MAX_NAME_W = 80;

    // ── 颜色 ──
    private static final int GRAY   = 0xFFAAAAAA;
    private static final int GREEN  = 0xFF55FF55;
    private static final int GOLD   = 0xFFFFD700;
    private static final int RED    = 0xFFFF5555;
    private static final int PURPLE = 0xFFCC66FF;
    private static final int ORANGE = 0xFFFFAA33;
    private static final int WHITE  = 0xFFFFFFFF;
    private static final int DIM    = 0xFF8888AA;

    // ── 布局常量 (460×254, 无背景) ──
    private static final int H_TITLE      = 8;    // 标题行
    private static final int H_INFO       = 22;   // 信息行
    private static final int H_VILL_NAME  = 36;   // 村民名字
    private static final int H_VILL_INFO  = 50;   // 村民信息 (牌数/资金)
    private static final int H_VILL_CARDS = 62;   // 村民出牌小卡 (34px → 96)
    private static final int H_MSG        = 102;  // 消息
    private static final int H_PLAY_LABEL = 112;  // 玩家出牌标签
    private static final int H_PLAY_CARDS = 122;  // 玩家出牌小卡 (34px → 156)
    private static final int H_BTN        = 162;  // 按钮 (20px → 182)
    private static final int H_HAND_LABEL = 186;  // 手牌标签
    private static final int H_HAND       = 196;  // 手牌大卡 (54px → 250)

    private static final int PANEL_LEFT  = 4;
    private static final int PANEL_RIGHT = 456;
    private static final int MID_X       = 230;

    // ── 状态 ──
    DouDiZhuSyncPayload state = createEmptyState();
    private boolean readySent;
    private Button btn1x, btn2x, btn4x, btn8x, btnExit;
    private Button btnBid1, btnBid2, btnBid3, btnPassBid;
    private Button btnPlay, btnPass2, btnHint;
    private final BitSet selected = new BitSet();
    private boolean dragging;
    private boolean dragSelect;

    // ── 各玩家最后出牌 (持久显示直到该玩家下一轮) ──
    private final List<Integer> lastCardsP0 = new ArrayList<>();
    private String lastTypeP0 = "";
    private final List<Integer> lastCardsP1 = new ArrayList<>();
    private String lastTypeP1 = "";
    private final List<Integer> lastCardsP2 = new ArrayList<>();
    private String lastTypeP2 = "";

    // ── 村民动作标记 (持久显示直到该村民下一轮) ──
    private boolean passedP1, passedP2;       // 不出
    private String bidInfo1 = "", bidInfo2 = ""; // 叫地主信息 ("叫了2分"/"不叫")

    public DouDiZhuGameScreen(DouDiZhuGameMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 460;
        this.imageHeight = 254;
        this.titleLabelY = -100;
        this.inventoryLabelY = -100;
    }

    private static DouDiZhuSyncPayload createEmptyState() {
        return new DouDiZhuSyncPayload(0, new ArrayList<>(), 0, 0, new ArrayList<>(),
                -1, -1, new ArrayList<>(), -1, "", "", "", "", 0, 0, 0, 0, 0, 0, false, -1);
    }

    /** 接收服务端同步，更新各玩家出牌记录 */
    public void updateGameState(DouDiZhuSyncPayload s) {
        // ── 出牌阶段: 轮到谁出牌 → 清掉他上一轮的出牌/不出 ──
        if (s.phase() == DouDiZhuGameMenu.PHASE_PLAYING) {
            if (s.currentPlayerIndex() == 0) { lastCardsP0.clear(); lastTypeP0 = ""; }
            if (s.currentPlayerIndex() == 1) { lastCardsP1.clear(); lastTypeP1 = ""; passedP1 = false; }
            if (s.currentPlayerIndex() == 2) { lastCardsP2.clear(); lastTypeP2 = ""; passedP2 = false; }
        }

        // ── 刚进入叫地主/出牌阶段 → 清空对应记录 ──
        if (s.phase() == DouDiZhuGameMenu.PHASE_BIDDING
                && state.phase() != DouDiZhuGameMenu.PHASE_BIDDING) {
            bidInfo1 = ""; bidInfo2 = "";
        }
        if (s.phase() == DouDiZhuGameMenu.PHASE_PLAYING
                && state.phase() != DouDiZhuGameMenu.PHASE_PLAYING) {
            lastCardsP0.clear(); lastTypeP0 = "";
            lastCardsP1.clear(); lastTypeP1 = "";
            lastCardsP2.clear(); lastTypeP2 = "";
            passedP1 = false; passedP2 = false;
            bidInfo1 = ""; bidInfo2 = "";
        }

        // ── 记录村民叫分/不出 (根据 lastActorIdx 精确判断) ──
        int actor = s.lastActorIdx();
        if (actor == 1 || actor == 2) {
            String msg = s.message();
            if (msg != null) {
                if (msg.contains("不出")) {
                    if (actor == 1) { passedP1 = true; lastCardsP1.clear(); }
                    else { passedP2 = true; lastCardsP2.clear(); }
                } else if (msg.contains("叫了") || msg.contains("不叫")) {
                    String action = clean(msg);
                    // 去掉名字前缀 (格式: "名字 动作")
                    String name = actor == 1 ? s.villager1Name() : s.villager2Name();
                    if (name != null && !name.isEmpty() && action.startsWith(name))
                        action = action.substring(name.length()).trim();
                    if (actor == 1) bidInfo1 = action;
                    else bidInfo2 = action;
                }
            }
        }

        // ── 记录新出牌 ──
        if (s.lastPlayedBy() == 0 && !s.lastPlayedCards().isEmpty()) {
            lastCardsP0.clear(); lastCardsP0.addAll(s.lastPlayedCards());
            lastTypeP0 = s.lastPlayedType();
        } else if (s.lastPlayedBy() == 1 && !s.lastPlayedCards().isEmpty()) {
            lastCardsP1.clear(); lastCardsP1.addAll(s.lastPlayedCards());
            lastTypeP1 = s.lastPlayedType(); passedP1 = false;
        } else if (s.lastPlayedBy() == 2 && !s.lastPlayedCards().isEmpty()) {
            lastCardsP2.clear(); lastCardsP2.addAll(s.lastPlayedCards());
            lastTypeP2 = s.lastPlayedType(); passedP2 = false;
        }

        state = s;
        selected.clear();
        rebuildButtons();
    }

    // ═══════════════════════════════════════════════════════════════
    // 按钮
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
        if (!readySent) {
            send(DouDiZhuGameMenu.ACTION_SCREEN_READY, 0, 0);
            readySent = true;
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        int cx = guiLeft(), cy = guiTop();

        switch (state.phase()) {
            case DouDiZhuGameMenu.PHASE_MULTIPLIER -> {
                int bw = 60, g = 10, total = bw * 4 + g * 3;
                int bx = cx + (imageWidth - total) / 2, y = cy + H_BTN;
                btn1x = multBtn("×1", 1, bx, y, bw);
                btn2x = multBtn("×2", 2, bx + bw + g, y, bw);
                btn4x = multBtn("×4", 4, bx + (bw + g) * 2, y, bw);
                btn8x = multBtn("×8", 8, bx + (bw + g) * 3, y, bw);
                btnExit = Button.builder(Component.literal("退出"), b -> onClose())
                        .bounds(cx + imageWidth / 2 - 30, cy + H_BTN + 24, 60, 20).build();
                addW(btn1x, btn2x, btn4x, btn8x, btnExit);
            }
            case DouDiZhuGameMenu.PHASE_BIDDING -> {
                int bw = 60, g = 10, total = bw * 4 + g * 3;
                int bx = cx + (imageWidth - total) / 2, y = cy + H_BTN;
                btnBid1 = bidBtn("1分", 1, bx, y, bw);
                btnBid2 = bidBtn("2分", 2, bx + bw + g, y, bw);
                btnBid3 = bidBtn("3分", 3, bx + (bw + g) * 2, y, bw);
                btnPassBid = bidBtn("不叫", 0, bx + (bw + g) * 3, y, bw);
                addW(btnBid1, btnBid2, btnBid3, btnPassBid);
            }
            case DouDiZhuGameMenu.PHASE_PLAYING -> {
                boolean my = state.currentPlayerIndex() == 0;
                boolean cp = my && state.lastPlayedBy() != 0 && state.lastPlayedBy() >= 0;
                boolean isLord = state.landlordIndex() == 0;
                int nBtns = (state.p2CardCount() == 0 || state.p3CardCount() == 0) ? 1 : (isLord ? 4 : 3);
                int bw = 60, g = 8, total = nBtns * bw + (nBtns - 1) * g;
                int bx = cx + (imageWidth - total) / 2, y = cy + H_BTN;

                btnPlay = Button.builder(Component.literal("出牌"), b -> doPlay())
                        .bounds(bx, y, bw, 20).build();
                btnPass2 = Button.builder(Component.literal("不出"), b -> send(DouDiZhuGameMenu.ACTION_PASS, 0, 0))
                        .bounds(bx + bw + g, y, bw, 20).build();
                btnHint = Button.builder(Component.literal("提示"), b -> doHint())
                        .bounds(bx + (bw + g) * 2, y, bw, 20).build();
                btnPlay.active = my;
                btnPass2.active = cp;
                btnHint.active = my;
                addW(btnPlay, btnPass2, btnHint);

                if (isLord) {
                    var btnSurrender = Button.builder(Component.literal("§c投降"),
                                    b -> send(DouDiZhuGameMenu.ACTION_SURRENDER, 0, 0))
                            .bounds(bx + (bw + g) * 3, y, bw, 20).build();
                    btnSurrender.active = my;
                    addRenderableWidget(btnSurrender);
                }
            }
            case DouDiZhuGameMenu.PHASE_FINISHED -> {
                var btnClose = Button.builder(Component.literal("关闭"), b -> onClose())
                        .bounds(cx + imageWidth / 2 - 30, cy + H_BTN + 4, 60, 20).build();
                addRenderableWidget(btnClose);
            }
        }
    }

    private Button multBtn(String t, int m, int x, int y, int w) {
        return Button.builder(Component.literal(t),
                b -> send(DouDiZhuGameMenu.ACTION_SELECT_MULTIPLIER, m, 0)).bounds(x, y, w, 20).build();
    }

    private Button bidBtn(String t, int bid, int x, int y, int w) {
        return Button.builder(Component.literal(t),
                b -> send(DouDiZhuGameMenu.ACTION_BID, bid, 0)).bounds(x, y, w, 20).build();
    }

    private void addW(Button... bs) {
        for (var b : bs) addRenderableWidget(b);
    }

    // ═══════════════════════════════════════════════════════════════
    // 网络
    // ═══════════════════════════════════════════════════════════════

    private void send(int a, int d, long m) {
        if (Minecraft.getInstance().getConnection() != null)
            Minecraft.getInstance().getConnection().send(new DouDiZhuActionPayload(a, d, m));
    }

    private void doPlay() {
        long m = 0;
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1))
            m |= (1L << i);
        if (m == 0) return;
        send(DouDiZhuGameMenu.ACTION_PLAY, 0, m);
        selected.clear();
    }

    private void doHint() {
        selected.clear();
        List<Integer> h = state.playerCards();
        if (h.isEmpty()) return;

        boolean resp = state.lastPlayedBy() >= 0 && state.lastPlayedBy() != 0
                && !state.lastPlayedCards().isEmpty();

        if (resp) {
            Map<Integer, List<Integer>> by = new LinkedHashMap<>();
            for (int i = 0; i < h.size(); i++)
                by.computeIfAbsent(DouDiZhuGameMenu.gameRank(h.get(i)), k -> new ArrayList<>()).add(i);

            int lr = DouDiZhuGameMenu.gameRank(state.lastPlayedCards().get(0));
            String lt = state.lastPlayedType();
            List<Integer> sorted = new ArrayList<>(by.keySet());
            sorted.sort(Integer::compareTo);

            for (int gr : sorted)
                if (gr > lr && by.get(gr).size() >= 1) {
                    selected.set(by.get(gr).get(0)); return;
                }
            if (lt.contains("对"))
                for (int gr : sorted)
                    if (gr > lr && by.get(gr).size() >= 2) {
                        selected.set(by.get(gr).get(0)); selected.set(by.get(gr).get(1)); return;
                    }
            if (lt.contains("三"))
                for (int gr : sorted)
                    if (gr > lr && by.get(gr).size() >= 3) {
                        for (int j = 0; j < 3; j++) selected.set(by.get(gr).get(j)); return;
                    }
            for (int gr : sorted)
                if (by.get(gr).size() == 4) {
                    for (int idx : by.get(gr)) selected.set(idx); return;
                }
            if (by.containsKey(14) && by.containsKey(15)) {
                selected.set(by.get(14).get(0));
                selected.set(by.get(15).get(0));
            }
        } else {
            int min = 0, mg = 99;
            for (int i = 0; i < h.size(); i++) {
                int g = DouDiZhuGameMenu.gameRank(h.get(i));
                if (g < mg) { mg = g; min = i; }
            }
            selected.set(min);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 鼠标
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (!dbl && e.button() == 0
                && state.phase() == DouDiZhuGameMenu.PHASE_PLAYING
                && state.currentPlayerIndex() == 0) {
            int idx = cardAt(e.x(), e.y());
            if (idx >= 0 && idx < state.playerCards().size()) {
                dragging = true;
                dragSelect = !selected.get(idx);
                selected.flip(idx);
                return true;
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (e.button() == 0) {
            dragging = false;
        }
        return super.mouseReleased(e);
    }

    private int cardAt(double mx, double my) {
        int cx = guiLeft(), cy = guiTop(), n = state.playerCards().size();
        int ov = handOv(n);
        int tw = n <= 1 ? CARD_W : CARD_W + (n - 1) * (CARD_W - ov);
        int sx = cx + (imageWidth - tw) / 2, y = cy + H_HAND;
        for (int i = n - 1; i >= 0; i--) {
            int x = sx + i * (CARD_W - ov);
            if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) return i;
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════
    // 主渲染
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int cx = guiLeft(), cy = guiTop();

        renderTitleBar(g, cx, cy);
        renderVillagers(g, cx, cy);
        renderVillagerPlays(g, cx, cy);
        renderPlayerPlay(g, cx, cy);

        switch (state.phase()) {
            case DouDiZhuGameMenu.PHASE_MULTIPLIER -> renderMultiplier(g, cx, cy);
            case DouDiZhuGameMenu.PHASE_BIDDING -> renderBidding(g, cx, cy);
            case DouDiZhuGameMenu.PHASE_PLAYING -> renderPlaying(g, cx, cy, true);
            case DouDiZhuGameMenu.PHASE_FINISHED -> renderFinished(g, cx, cy);
        }

        super.render(g, mx, my, pt);

        // 拖拽选牌
        if (dragging && state.phase() == DouDiZhuGameMenu.PHASE_PLAYING
                && state.currentPlayerIndex() == 0) {
            int idx = cardAt(mx, my);
            if (idx >= 0 && idx < state.playerCards().size()) {
                if (dragSelect && !selected.get(idx))
                    selected.set(idx);
                else if (!dragSelect && selected.get(idx))
                    selected.clear(idx);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 标题栏 (底牌以文字形式显示在屏幕正上方中央)
    // ═══════════════════════════════════════════════════════════════

    private void renderTitleBar(GuiGraphics g, int cx, int cy) {
        int absMid = cx + MID_X;
        int absRight = cx + PANEL_RIGHT;
        int absLeft = cx + PANEL_LEFT;

        // 左侧: 标题
        draw(g, "◆ 斗地主", absLeft + 6, cy + H_TITLE, GOLD);

        // 中间: 底牌文字 (揭晓后) 或 阶段标签 (未揭晓)
        if (state.bottomRevealed() && !state.bottomCards().isEmpty()) {
            String bcText = bottomCardsText();
            drawC(g, bcText, absMid, cy + H_TITLE, PURPLE);
        } else {
            String phaseLabel = switch (state.phase()) {
                case DouDiZhuGameMenu.PHASE_MULTIPLIER -> "选择倍率";
                case DouDiZhuGameMenu.PHASE_BIDDING -> "叫地主";
                case DouDiZhuGameMenu.PHASE_PLAYING -> "出牌中";
                case DouDiZhuGameMenu.PHASE_FINISHED -> "已结束";
                default -> "";
            };
            drawC(g, phaseLabel, absMid, cy + H_TITLE, DIM);
        }

        // 右侧: 绿宝石
        drawR(g, "◆ " + countEmeralds(), absRight - 2, cy + H_TITLE, GREEN);

        // 第二行: 底注 + 倍率 + 叫分
        StringBuilder info = new StringBuilder();
        info.append("底注:").append(state.baseStake());
        if (state.multiplier() > 0)
            info.append("  ×").append(state.multiplier());
        if (state.bidMultiplier() > 0)
            info.append("  叫分:").append(state.bidMultiplier());
        draw(g, info.toString(), absLeft + 6, cy + H_INFO, ORANGE);
    }

    /** 底牌 → 彩色文字, 如 "底牌: ♥K ♦3 ♣7" */
    private String bottomCardsText() {
        List<Integer> bc = state.bottomCards();
        StringBuilder sb = new StringBuilder("底牌: ");
        for (int i = 0; i < bc.size(); i++) {
            if (i > 0) sb.append("  ");
            int c = bc.get(i);
            sb.append(DouDiZhuGameMenu.suitDisplay(c));
            sb.append(DouDiZhuGameMenu.rankDisplay(DouDiZhuGameMenu.gameRank(c)));
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    // 村民 (名字 + 信息)
    // ═══════════════════════════════════════════════════════════════

    private void renderVillagers(GuiGraphics g, int cx, int cy) {
        int absLeft = cx + PANEL_LEFT;
        int absRight = cx + PANEL_RIGHT;

        boolean lL = state.landlordIndex() == 1, rL = state.landlordIndex() == 2;
        boolean lT = state.currentPlayerIndex() == 1, rT = state.currentPlayerIndex() == 2;

        // 左侧村民: 名字
        StringBuilder lb = new StringBuilder();
        if (lT) lb.append("§e▶ ");
        if (lL) lb.append("§6👑");
        lb.append(trunc(state.villager1Name()));
        draw(g, lb.toString(), absLeft + 6, cy + H_VILL_NAME, lL ? GOLD : WHITE);

        // 左侧村民: 信息
        draw(g, "牌×" + state.p2CardCount() + "  资:" + state.villager1Budget(),
                absLeft + 6, cy + H_VILL_INFO, 0xFFCCAA55);

        // 右侧村民: 名字
        StringBuilder rb = new StringBuilder();
        rb.append(trunc(state.villager2Name()));
        if (rL) rb.append("§6👑");
        if (rT) rb.append(" §e◀");
        drawR(g, rb.toString(), absRight - 2, cy + H_VILL_NAME, rL ? GOLD : WHITE);

        // 右侧村民: 信息
        drawR(g, "牌×" + state.p3CardCount() + "  资:" + state.villager2Budget(),
                absRight - 2, cy + H_VILL_INFO, 0xFFCCAA55);
    }

    // ═══════════════════════════════════════════════════════════════
    // 村民出牌展示 (各自位置, 持久显示直到该村民再次出牌)
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    // 村民动作展示 (叫分 / 出牌 / 不出 — 各自身份, 持久显示)
    // ═══════════════════════════════════════════════════════════════

    private void renderVillagerPlays(GuiGraphics g, int cx, int cy) {
        int absLeft = cx + PANEL_LEFT;
        int absRight = cx + PANEL_RIGHT;

        int y = cy + H_VILL_CARDS + 4;

        // ── 左侧村民 ──
        if (state.phase() == DouDiZhuGameMenu.PHASE_BIDDING && !bidInfo1.isEmpty()) {
            drawBidInfo(g, bidInfo1, absLeft + 6, y);
        } else if (passedP1) {
            drawPassBadge(g, absLeft + 6, y);
        } else if (!lastCardsP1.isEmpty()) {
            for (int i = 0; i < lastCardsP1.size(); i++)
                renderSmallCard(g, lastCardsP1.get(i),
                        absLeft + 6 + i * (SMALL_W + 3), cy + H_VILL_CARDS);
        }

        // ── 右侧村民 ──
        if (state.phase() == DouDiZhuGameMenu.PHASE_BIDDING && !bidInfo2.isEmpty()) {
            drawBidInfoR(g, bidInfo2, absRight - 2, y);
        } else if (passedP2) {
            drawPassBadgeR(g, absRight - 2, y);
        } else if (!lastCardsP2.isEmpty()) {
            int tw = lastCardsP2.size() * SMALL_W + (lastCardsP2.size() - 1) * 3;
            int sx = absRight - 2 - tw;
            for (int i = 0; i < lastCardsP2.size(); i++)
                renderSmallCard(g, lastCardsP2.get(i),
                        sx + i * (SMALL_W + 3), cy + H_VILL_CARDS);
        }
    }

    /** 左侧叫分信息 (统一徽章样式, 叫分=金框金底, 不叫=灰框灰底) */
    private void drawBidInfo(GuiGraphics g, String info, int x, int y) {
        boolean pass = info.contains("不叫");
        drawBadge(g, pass ? "不叫" : info, x, y, false, pass ? GRAY : GOLD);
    }

    /** 右侧叫分信息 */
    private void drawBidInfoR(GuiGraphics g, String info, int x, int y) {
        boolean pass = info.contains("不叫");
        drawBadge(g, pass ? "不叫" : info, x, y, true, pass ? GRAY : GOLD);
    }

    /** 左侧"不出"徽章 */
    private void drawPassBadge(GuiGraphics g, int x, int y) {
        drawBadge(g, "不出", x, y, false, WHITE);
    }

    /** 右侧"不出"徽章 */
    private void drawPassBadgeR(GuiGraphics g, int x, int y) {
        drawBadge(g, "不出", x, y, true, WHITE);
    }

    /** 通用徽章: 深色底+双层边框+粗体文字, 比普通文字更醒目 */
    private void drawBadge(GuiGraphics g, String text, int x, int y, boolean rightAlign, int textColor) {
        int tw = font.width(text);
        int padX = 6, padY = 4;
        int bgW = tw + padX * 2;
        int bgH = 12 + padY * 2;
        int bgX = rightAlign ? x - bgW : x;
        int tx = bgX + padX;

        // 外边框
        g.renderOutline(bgX - 1, y - 2, bgW + 2, bgH + 3, 0xFF555555);
        // 深色底
        g.fill(bgX, y - 1, bgX + bgW, y - 1 + bgH, 0xDD111122);
        // 内边框 (双线效果)
        g.renderOutline(bgX, y - 1, bgW, bgH, 0xFF999999);
        // 粗体文字
        draw(g, "§l" + text, tx, y + padY - 1, textColor);
    }

    // ═══════════════════════════════════════════════════════════════
    // 玩家出牌展示 (屏幕中央, 持久显示直到再次轮到玩家)
    // ═══════════════════════════════════════════════════════════════

    private void renderPlayerPlay(GuiGraphics g, int cx, int cy) {
        int absMid = cx + MID_X;

        if (lastCardsP0.isEmpty()) return;

        // 仅对复杂牌型显示文字标签 (顺子/连对/飞机/四带/炸弹/火箭)
        String type = lastTypeP0;
        boolean complex = type.contains("顺") || type.contains("连") || type.contains("飞机")
                || type.contains("四带") || type.contains("炸弹") || type.contains("火箭");
        if (complex) {
            drawC(g, "你 → " + type, absMid, cy + H_PLAY_LABEL, GREEN);
        }

        // 小卡居中
        int n = lastCardsP0.size();
        int tw = n * SMALL_W + (n - 1) * 3;
        int sx = absMid - tw / 2;
        for (int i = 0; i < n; i++)
            renderSmallCard(g, lastCardsP0.get(i), sx + i * (SMALL_W + 3), cy + H_PLAY_CARDS);
    }

    // ═══════════════════════════════════════════════════════════════
    // 各阶段渲染
    // ═══════════════════════════════════════════════════════════════

    private void renderMultiplier(GuiGraphics g, int cx, int cy) {
        int absMid = cx + MID_X;
        if (!state.message().isEmpty() && !isVillagerStatusMsg())
            drawC(g, clean(state.message()), absMid, cy + H_MSG, msgCol(state.message()));
        else
            drawC(g, "请选择倍率 (底注 " + state.baseStake() + " 绿宝石)", absMid, cy + H_MSG, GRAY);
    }

    private void renderBidding(GuiGraphics g, int cx, int cy) {
        int absMid = cx + MID_X;
        if (!state.message().isEmpty() && !isVillagerStatusMsg())
            drawC(g, clean(state.message()), absMid, cy + H_MSG, msgCol(state.message()));
        else
            drawC(g, "§l叫地主阶段", absMid, cy + H_MSG, GOLD);

        renderHand(g, cx, cy, false);
    }

    private void renderPlaying(GuiGraphics g, int cx, int cy, boolean sel) {
        int absLeft = cx + PANEL_LEFT;
        int absMid = cx + MID_X;

        if (!state.message().isEmpty() && !isVillagerStatusMsg())
            drawC(g, clean(state.message()), absMid, cy + H_MSG, msgCol(state.message()));

        boolean isLord = state.landlordIndex() == 0;
        boolean isMyTurn = state.currentPlayerIndex() == 0;
        StringBuilder handLabel = new StringBuilder();
        handLabel.append("你的手牌  ").append(state.playerCards().size()).append("张");
        if (isLord) handLabel.append("  §6👑地主");
        if (isMyTurn && sel) handLabel.append("  §e◀ 到你出牌");
        int labelCol = isMyTurn && sel ? GOLD : (isLord ? GOLD : GREEN);
        draw(g, handLabel.toString(), absLeft + 6, cy + H_HAND_LABEL, labelCol);

        renderHand(g, cx, cy, sel);
    }

    private void renderFinished(GuiGraphics g, int cx, int cy) {
        int absMid = cx + MID_X;

        if (!state.message().isEmpty() && !isVillagerStatusMsg())
            drawC(g, clean(state.message()), absMid, cy + H_MSG, msgCol(state.message()));

        draw(g, "你的手牌  " + state.playerCards().size() + "张",
                cx + PANEL_LEFT + 6, cy + H_HAND_LABEL, GREEN);

        renderHand(g, cx, cy, false);

        // 结算横幅
        if (state.result() != DouDiZhuGameMenu.RESULT_NONE) {
            boolean win = (state.result() == DouDiZhuGameMenu.RESULT_LANDLORD_WIN && state.landlordIndex() == 0)
                    || (state.result() == DouDiZhuGameMenu.RESULT_FARMER_WIN && state.landlordIndex() != 0);
            int col = win ? GOLD : RED;
            String big = win ? "★ 你赢了 ★" : "☆ 你输了 ☆";

            int bw = 220, bh = 32;
            int bx = cx + (imageWidth - bw) / 2, by = cy + H_HAND - 10;
            g.fill(bx, by, bx + bw, by + bh, 0xEE111122);
            g.renderOutline(bx, by, bw, bh, win ? GOLD : RED);
            drawC(g, big, cx + MID_X, by + 4, col);

            if (!state.message().isEmpty())
                drawC(g, clean(state.message()), cx + MID_X, by + 18, 0xFFCCCCCC);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 手牌
    // ═══════════════════════════════════════════════════════════════

    private int handOv(int n) {
        if (n <= 1) return 0;
        int need = (n * CARD_W - (imageWidth - 24)) / (n - 1);
        return Math.max(MIN_OV, Math.min(MAX_OV, need));
    }

    private void renderHand(GuiGraphics g, int cx, int cy, boolean sel) {
        List<Integer> h = state.playerCards();
        int n = h.size();
        if (n == 0) return;

        int ov = handOv(n);
        int tw = n <= 1 ? CARD_W : CARD_W + (n - 1) * (CARD_W - ov);
        int sx = cx + (imageWidth - tw) / 2, y = cy + H_HAND;

        for (int i = 0; i < n; i++) {
            if (sel && selected.get(i)) continue;
            renderCard(g, h.get(i), sx + i * (CARD_W - ov), y);
        }

        for (int i = 0; i < n; i++) {
            if (!sel || !selected.get(i)) continue;
            int x = sx + i * (CARD_W - ov), ySel = y - 14;
            g.fill(x + 2, ySel + 2, x + CARD_W + 2, ySel + CARD_H + 2, 0x44000000);
            renderCard(g, h.get(i), x, ySel);
            g.renderOutline(x - 1, ySel - 1, CARD_W + 2, CARD_H + 2, GOLD);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 卡牌渲染
    // ═══════════════════════════════════════════════════════════════

    private void renderCard(GuiGraphics g, int c, int x, int y) {
        int gr = DouDiZhuGameMenu.gameRank(c);
        int col = DouDiZhuGameMenu.suitColor(c);
        String r = DouDiZhuGameMenu.rankDisplay(gr);
        String s = DouDiZhuGameMenu.suitDisplay(c);

        g.fill(x, y, x + CARD_W, y + CARD_H, 0xFFFCFCF8);
        g.renderOutline(x, y, CARD_W, CARD_H, 0xFF888888);

        draw(g, r, x + 3, y + 2, col);
        if (!s.isEmpty()) {
            draw(g, s, x + 3, y + 15, col);
            drawC(g, s, x + CARD_W / 2, y + 22, col);
        } else {
            drawC(g, r, x + CARD_W / 2, y + 12, col);
        }
    }

    private void renderSmallCard(GuiGraphics g, int c, int x, int y) {
        int gr = DouDiZhuGameMenu.gameRank(c);
        int col = DouDiZhuGameMenu.suitColor(c);
        String r = DouDiZhuGameMenu.rankDisplay(gr);
        String s = DouDiZhuGameMenu.suitDisplay(c);

        g.fill(x, y, x + SMALL_W, y + SMALL_H, 0xFFFCFCF8);
        g.renderOutline(x, y, SMALL_W, SMALL_H, 0xFF888888);

        draw(g, r, x + 2, y + 1, col);
        if (!s.isEmpty())
            draw(g, s, x + 2, y + 12, col);
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    private String trunc(String n) {
        if (n == null || n.isEmpty()) return "?";
        if (font.width(n) <= MAX_NAME_W) return n;
        for (int i = n.length() - 1; i > 0; i--)
            if (font.width(n.substring(0, i)) <= MAX_NAME_W - font.width("…"))
                return n.substring(0, i) + "…";
        return n.charAt(0) + "…";
    }

    private void draw(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(font, s, x, y, c);
    }

    private void drawC(GuiGraphics g, String s, int cx, int y, int c) {
        g.drawCenteredString(font, s, cx, y, c);
    }

    private void drawR(GuiGraphics g, String s, int r, int y, int c) {
        g.drawString(font, s, r - font.width(s), y, c);
    }

    private int countEmeralds() {
        if (minecraft == null || minecraft.player == null) return 0;
        int n = 0;
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(Items.EMERALD))
                n += inv.getItem(i).getCount();
        return n;
    }

    /** 消息是村民叫分/不出 → 不在屏幕中央显示 (在村民位置显示) */
    private boolean isVillagerStatusMsg() {
        int actor = state.lastActorIdx();
        if (actor != 1 && actor != 2) return false;
        String m = state.message();
        return m != null && (m.contains("不出") || m.contains("叫了") || m.contains("不叫"));
    }

    private static String clean(String m) {
        return m == null ? "" : m.replaceAll("§[0-9a-fk-or]", "");
    }

    private static int msgCol(String m) {
        if (m == null) return GRAY;
        if (m.startsWith("§c")) return RED;
        if (m.startsWith("§a")) return GREEN;
        if (m.startsWith("§7")) return GRAY;
        if (m.startsWith("§d")) return PURPLE;
        if (m.startsWith("§e")) return GOLD;
        if (m.startsWith("§6")) return ORANGE;
        return GOLD;
    }

    private int guiLeft() { return (width - imageWidth) / 2; }
    private int guiTop() { return (height - imageHeight) / 2; }
}

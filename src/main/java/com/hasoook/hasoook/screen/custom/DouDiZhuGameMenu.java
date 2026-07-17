package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.item.custom.PokerItem;
import com.hasoook.hasoook.network.payload.DouDiZhuSyncPayload;
import com.hasoook.hasoook.screen.ModMenuTypes;
import com.hasoook.hasoook.util.TickScheduler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 斗地主游戏菜单 —— 服务端游戏逻辑
 * <p>
 * 3人游戏（1玩家 + 2村民AI），54张牌（含大小王）
 * 流程：发牌 → 叫地主 → 出牌 → 结算
 */
public class DouDiZhuGameMenu extends AbstractContainerMenu {

    // ── 阶段 ──
    public static final int PHASE_MULTIPLIER = 0;
    public static final int PHASE_BIDDING = 1;
    public static final int PHASE_PLAYING = 2;
    public static final int PHASE_FINISHED = 3;

    // ── 结果 ──
    public static final int RESULT_NONE = 0;
    public static final int RESULT_LANDLORD_WIN = 1;
    public static final int RESULT_FARMER_WIN = 2;

    // ── 动作 ──
    public static final int ACTION_SELECT_MULTIPLIER = 0;
    public static final int ACTION_BID = 1;
    public static final int ACTION_PLAY = 2;
    public static final int ACTION_PASS = 3;
    public static final int ACTION_SURRENDER = 4;
    public static final int ACTION_SCREEN_READY = 5;

    public static final int[] MULTIPLIERS = {1, 2, 4, 8};
    private static final int AI_DELAY = 15; // AI操作间隔(tick)

    // ── 牌型 ──
    enum CardType {
        SINGLE, PAIR, TRIPLE, TRIPLE_ONE, TRIPLE_TWO,
        STRAIGHT, STRAIGHT_PAIRS, AIRPLANE, AIRPLANE_SINGLES, AIRPLANE_PAIRS,
        FOUR_TWO, FOUR_TWO_PAIRS, BOMB, ROCKET, INVALID;

        /** 炸弹及火箭可压任意牌型 */
        boolean isBombOrRocket() { return this == BOMB || this == ROCKET; }
    }

    /** 牌型分析结果 */
    record HandInfo(CardType type, int primaryRank, int extraCount, List<Integer> cards) {}

    // ── 村民信息 ──
    record VillagerInfo(String name, int entityId, int budget, boolean isNitwit) {}

    // ── 游戏状态 ──
    private final ServerPlayer player;
    private final VillagerInfo villager1, villager2;
    private int phase = PHASE_MULTIPLIER;
    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> hand0 = new ArrayList<>(); // 玩家
    private final List<Integer> hand1 = new ArrayList<>(); // 村民1
    private final List<Integer> hand2 = new ArrayList<>(); // 村民2
    private final List<Integer> bottomCards = new ArrayList<>(); // 3张底牌
    private int landlordIndex = -1;    // 0=玩家, 1=村民1, 2=村民2
    private int currentPlayerIdx = 0;
    private List<Integer> lastPlayedCards = new ArrayList<>();
    private int lastPlayedBy = -1;
    private CardType lastPlayedType = CardType.INVALID;
    private int lastPlayedRank = -1;
    private int lastPlayedExtra = 0;   // 额外参数 (顺子张数/连对对数等)
    private int passCount = 0;         // 连续"不出"次数
    private int bidderIdx = 0;         // 当前叫牌人
    private int highestBid = 0;        // 最高叫分
    private int highestBidder = -1;    // 最高叫分者
    private int baseStake;
    private int multiplier;
    private int bidMultiplier = 1;     // 叫地主倍率
    private int bombCount = 0;         // 炸弹/火箭计数（翻倍）
    private String message = "";
    private boolean bottomRevealed = false;
    private boolean betPaid = false;
    private int lastActorIdx = -1;       // 最近一次动作执行者 (0/1/2)

    // ── 构造函数 ──

    /** 客户端 */
    public DouDiZhuGameMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.DOUDIZHU_GAME_MENU.get(), containerId);
        this.player = null;
        this.villager1 = new VillagerInfo("", -1, 0, false);
        this.villager2 = new VillagerInfo("", -1, 0, false);
        this.baseStake = 0;
    }

    /** 服务端 */
    public DouDiZhuGameMenu(int containerId, Inventory inv, ServerPlayer player,
                            VillagerInfo v1, VillagerInfo v2, int baseStake) {
        super(ModMenuTypes.DOUDIZHU_GAME_MENU.get(), containerId);
        this.player = player;
        this.villager1 = v1;
        this.villager2 = v2;
        this.baseStake = baseStake;
    }

    // ═══════════════════════════════════════════════════════════════
    // 动作分发
    // ═══════════════════════════════════════════════════════════════

    public void handleAction(int action, int data, long cardMask) {
        message = "";
        switch (action) {
            case ACTION_SELECT_MULTIPLIER -> selectMultiplier(data);
            case ACTION_BID -> playerBid(data);
            case ACTION_PLAY -> playerPlay(cardMask);
            case ACTION_PASS -> playerPass();
            case ACTION_SURRENDER -> playerSurrender();
            case ACTION_SCREEN_READY -> sync();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 倍率选择
    // ═══════════════════════════════════════════════════════════════

    private void selectMultiplier(int mult) {
        if (phase != PHASE_MULTIPLIER) return;
        boolean ok = false;
        for (int m : MULTIPLIERS) if (m == mult) { ok = true; break; }
        if (!ok) return;

        int bet = baseStake * mult;
        if (countEmeralds(player) < bet) {
            message = "§c你需要 " + bet + " 颗绿宝石！（底注" + baseStake + " ×" + mult + "）";
            sync(); return;
        }
        // 消耗扑克牌耐久
        for (var hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof PokerItem) {
                held.hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                break;
            }
        }
        removeEmeralds(player, bet);
        this.multiplier = mult;
        this.betPaid = true;
        sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
        startBidding();
    }

    // ═══════════════════════════════════════════════════════════════
    // 叫地主
    // ═══════════════════════════════════════════════════════════════

    private void startBidding() {
        phase = PHASE_BIDDING;
        initDeck();
        dealCards();
        // 随机起始叫牌人
        bidderIdx = player.level().getRandom().nextInt(3);
        highestBid = 0;
        highestBidder = -1;
        message = "§e叫地主阶段 —— 等待叫牌...";
        sync();
        scheduleBidTurn();
    }

    private void scheduleBidTurn() {
        if (player.level() instanceof ServerLevel sl)
            TickScheduler.schedule(sl, AI_DELAY, this::processBidTurn);
    }

    private void processBidTurn() {
        if (phase != PHASE_BIDDING) return;

        // 如果已经有人叫了3分，直接结束
        if (highestBid >= 3) {
            finishBidding();
            return;
        }

        if (bidderIdx == 0) {
            // 玩家叫牌 —— 等待客户端输入
            message = "§e请选择：叫地主（1分/2分/3分）或不叫";
            sync();
        } else {
            // AI叫牌
            aiBid(bidderIdx);
        }
    }

    private void playerBid(int bid) {
        if (phase != PHASE_BIDDING || bidderIdx != 0) return;
        if (bid < 0 || bid > 3) return;
        if (bid > 0 && bid <= highestBid) {
            message = "§c叫分必须大于当前最高分 " + highestBid + "！";
            sync(); return;
        }
        processBid(0, bid);
    }

    private void aiBid(int idx) {
        List<Integer> hand = getHand(idx);
        int strength = evaluateHandStrength(hand, false);

        int bid;
        if (strength >= 80) bid = 3;
        else if (strength >= 60) bid = highestBid < 2 ? 2 : 0;
        else if (strength >= 40 && highestBid == 0) bid = 1;
        else bid = 0;

        processBid(idx, bid);
    }

    private void processBid(int idx, int bid) {
        lastActorIdx = idx;
        String name = getPlayerName(idx);
        if (bid > 0) {
            highestBid = bid;
            highestBidder = idx;
            message = "§e" + name + " 叫了 " + bid + " 分！";
        } else {
            message = "§7" + name + " 不叫";
        }

        // 检查是否所有人都叫过了
        bidderIdx = (bidderIdx + 2) % 3; // 逆时针

        // 如果绕了一圈回到最高叫分者，或者所有人都叫过了
        int totalBids = 0;
        // 简化：叫完一圈看结果
        if (bidderIdx == highestBidder || (highestBidder < 0 && bidderIdx == 0)) {
            // 再给一轮机会 — 如果没人叫，重新发牌
            if (highestBidder < 0) {
                message = "§7没人叫地主，重新发牌...";
                sync();
                if (player.level() instanceof ServerLevel sl)
                    TickScheduler.schedule(sl, 30, this::startBidding);
                return;
            }
            finishBidding();
            return;
        }

        sync();
        scheduleBidTurn();
    }

    private void finishBidding() {
        landlordIndex = highestBidder;
        bidMultiplier = highestBid;

        // 地主拿走底牌
        List<Integer> landlordHand = getHand(landlordIndex);
        landlordHand.addAll(bottomCards);
        sortHand(landlordHand);

        bottomRevealed = true;
        phase = PHASE_PLAYING;
        currentPlayerIdx = landlordIndex;
        lastPlayedBy = -1;
        lastPlayedCards.clear();
        lastPlayedType = CardType.INVALID;
        passCount = 0;

        String landlordName = getPlayerName(landlordIndex);
        message = "§6" + landlordName + " 成为地主！底牌已揭晓，开始出牌";
        sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.2F);
        sync();

        // 如果地主是AI，安排AI出牌
        if (landlordIndex != 0) scheduleAiTurn();
    }

    // ═══════════════════════════════════════════════════════════════
    // 出牌阶段
    // ═══════════════════════════════════════════════════════════════

    private void playerPlay(long cardMask) {
        if (phase != PHASE_PLAYING || currentPlayerIdx != 0) return;

        List<Integer> selected = cardsFromMask(hand0, cardMask);
        if (selected.isEmpty()) { message = "§c请选择要出的牌"; sync(); return; }

        HandInfo info = analyzeHand(selected);
        if (info.type == CardType.INVALID) {
            message = "§c无效牌型！"; sync(); return;
        }

        // 检查是否能压过上一手
        if (lastPlayedBy != 0 && lastPlayedBy >= 0) {
            if (!canBeat(info, lastPlayedType, lastPlayedRank)) {
                message = "§c打不过，请重新选择或不出"; sync(); return;
            }
        }

        executePlay(0, selected, info);
    }

    private void playerPass() {
        if (phase != PHASE_PLAYING || currentPlayerIdx != 0) return;
        if (lastPlayedBy == 0 || lastPlayedBy < 0) {
            message = "§c新一轮必须出牌！"; sync(); return;
        }
        doPass(0);
    }

    private void playerSurrender() {
        if (phase != PHASE_PLAYING) return;
        if (landlordIndex != 0) return; // 只有地主能投降
        sound(SoundEvents.VILLAGER_NO, 1.0F, 0.7F);
        message = "§c你投降了！";
        finishGame(RESULT_FARMER_WIN); // 农民获胜
    }

    private void scheduleAiTurn() {
        if (player.level() instanceof ServerLevel sl)
            TickScheduler.schedule(sl, AI_DELAY, this::aiTurn);
    }

    private void aiTurn() {
        if (phase != PHASE_PLAYING) return;
        int idx = currentPlayerIdx;
        if (idx == 0) return; // 不是AI

        List<Integer> hand = getHand(idx);
        boolean isLandlord = (idx == landlordIndex);

        if (lastPlayedBy == idx || lastPlayedBy < 0) {
            // 新一轮，AI先手出牌
            HandInfo best = chooseStartingHand(hand, isLandlord);
            if (best != null) {
                executePlay(idx, new ArrayList<>(best.cards), best);
            }
        } else {
            // 需要回应
            // 如果是队友（都是农民），且队友的牌还剩很少，尽量不出
            boolean isTeammate = !isLandlord && lastPlayedBy != landlordIndex && lastPlayedBy >= 0;
            if (isTeammate && hand.size() <= 2) {
                doPass(idx); return;
            }

            HandInfo response = chooseResponse(hand, lastPlayedType, lastPlayedRank, isLandlord);
            if (response != null) {
                executePlay(idx, new ArrayList<>(response.cards), response);
            } else {
                doPass(idx);
            }
        }
    }

    private void executePlay(int idx, List<Integer> cards, HandInfo info) {
        lastActorIdx = idx;
        // 从手牌移除
        List<Integer> hand = getHand(idx);
        // 使用iterator安全移除
        List<Integer> toRemove = new ArrayList<>(cards);
        hand.removeAll(toRemove);

        lastPlayedCards = cards;
        lastPlayedBy = idx;
        lastPlayedType = info.type;
        lastPlayedRank = info.primaryRank;
        lastPlayedExtra = info.extraCount();
        passCount = 0;

        if (info.type.isBombOrRocket()) {
            bombCount++;
            multiplier *= 2;
            message = "§d炸弹！倍率翻倍 ×" + multiplier;
        } else {
            message = ""; // 普通出牌：客户端通过 lastPlayedType 显示牌型，无需重复消息
        }

        sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.7F, 0.9F);

        // 检查是否出完
        if (hand.isEmpty()) {
            if (idx == landlordIndex) {
                finishGame(RESULT_LANDLORD_WIN);
            } else {
                finishGame(RESULT_FARMER_WIN);
            }
            return;
        }

        // 下一位
        currentPlayerIdx = (idx + 2) % 3;
        sync();
        if (currentPlayerIdx != 0) scheduleAiTurn();
    }

    private void doPass(int idx) {
        lastActorIdx = idx;
        passCount++;
        String name = getPlayerName(idx);
        message = "§7" + name + " 不出";
        sound(SoundEvents.VILLAGER_NO, 0.5F, 0.8F);

        // 2人连续不出 → 新一轮，最后出牌者继续出牌
        if (passCount >= 2) {
            int starter = lastPlayedBy;          // 保存：最后一个出牌的人
            lastPlayedBy = -1;
            lastPlayedCards.clear();
            lastPlayedType = CardType.INVALID;
            passCount = 0;
            currentPlayerIdx = starter;
        } else {
            currentPlayerIdx = (idx + 2) % 3;    // 逆时针下一位
        }

        sync();
        if (currentPlayerIdx != 0) scheduleAiTurn();
    }

    // ═══════════════════════════════════════════════════════════════
    // 牌型分析
    // ═══════════════════════════════════════════════════════════════

    /** 分析一手牌的牌型 */
    static HandInfo analyzeHand(List<Integer> cards) {
        if (cards.isEmpty()) return new HandInfo(CardType.INVALID, -1, 0, cards);
        int n = cards.size();
        List<Integer> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingInt(DouDiZhuGameMenu::gameRank));

        // 统计每个rank的数量
        Map<Integer, Integer> rankCount = new LinkedHashMap<>();
        for (int c : sorted) {
            int gr = gameRank(c);
            rankCount.merge(gr, 1, Integer::sum);
        }

        // 火箭：大小王
        if (n == 2 && sorted.get(0) == 52 && sorted.get(1) == 53)
            return new HandInfo(CardType.ROCKET, 15, 0, cards);

        // 炸弹：4张同rank
        if (n == 4 && rankCount.size() == 1 && rankCount.values().iterator().next() == 4) {
            int gr = rankCount.keySet().iterator().next();
            return new HandInfo(CardType.BOMB, gr, 0, cards);
        }

        List<Integer> ranks = new ArrayList<>(rankCount.keySet());
        List<Integer> counts = new ArrayList<>(rankCount.values());

        // 单牌
        if (n == 1) return new HandInfo(CardType.SINGLE, ranks.get(0), 0, cards);

        // 对子
        if (n == 2 && counts.get(0) == 2)
            return new HandInfo(CardType.PAIR, ranks.get(0), 0, cards);

        // 三条
        if (n == 3 && counts.get(0) == 3)
            return new HandInfo(CardType.TRIPLE, ranks.get(0), 0, cards);

        // 三带一 / 三带二 / 四带二 / 四带两对
        if (n == 4) {
            // 三带一
            for (int i = 0; i < ranks.size(); i++) {
                if (counts.get(i) == 3) {
                    int mainRank = ranks.get(i);
                    return new HandInfo(CardType.TRIPLE_ONE, mainRank, 1, cards);
                }
            }
            // 炸弹已在上面处理
        }

        if (n == 5) {
            // 三带二
            for (int i = 0; i < ranks.size(); i++) {
                if (counts.get(i) == 3) {
                    for (int j = 0; j < ranks.size(); j++) {
                        if (i != j && counts.get(j) == 2) {
                            return new HandInfo(CardType.TRIPLE_TWO, ranks.get(i), 1, cards);
                        }
                    }
                    return new HandInfo(CardType.INVALID, -1, 0, cards); // 三带一不对
                }
            }
            // 顺子 (5张)
            if (isStraight(ranks, counts, 5))
                return new HandInfo(CardType.STRAIGHT, ranks.get(ranks.size() - 1), ranks.size(), cards);
        }

        // 顺子 (6-12张)
        if (n >= 6 && n <= 12 && isStraight(ranks, counts, n))
            return new HandInfo(CardType.STRAIGHT, ranks.get(ranks.size() - 1), n, cards);

        // 连对 (>=3对)
        if (n >= 6 && n % 2 == 0 && counts.stream().allMatch(c -> c == 2)
                && isConsecutive(ranks, n / 2))
            return new HandInfo(CardType.STRAIGHT_PAIRS, ranks.get(ranks.size() - 1), n / 2, cards);

        // 四带二 (6张)
        if (n == 6) {
            for (int i = 0; i < ranks.size(); i++) {
                if (counts.get(i) == 4) {
                    return new HandInfo(CardType.FOUR_TWO, ranks.get(i), 2, cards);
                }
            }
        }

        // 四带两对 (8张)
        if (n == 8) {
            int fourCount = 0, pairCount = 0, fourRank = 0;
            for (int i = 0; i < ranks.size(); i++) {
                if (counts.get(i) == 4) { fourCount++; fourRank = ranks.get(i); }
                else if (counts.get(i) == 2) pairCount++;
            }
            if (fourCount == 1 && pairCount == 2)
                return new HandInfo(CardType.FOUR_TWO_PAIRS, fourRank, 2, cards);
        }

        // 飞机 (连续三条 >=2组)
        List<Integer> tripleRanks = new ArrayList<>();
        for (int i = 0; i < ranks.size(); i++)
            if (counts.get(i) >= 3) tripleRanks.add(ranks.get(i));
        int tripleCount = tripleRanks.size();
        if (tripleCount >= 2) {
            // 找到最长的连续三条
            int bestStart = 0, bestLen = 1;
            int start = 0;
            for (int i = 1; i < tripleRanks.size(); i++) {
                if (tripleRanks.get(i) == tripleRanks.get(i - 1) + 1) {
                    int len = i - start + 1;
                    if (len > bestLen) { bestLen = len; bestStart = start; }
                } else {
                    start = i;
                }
            }
            if (bestLen >= 2) {
                int planeRanks = bestLen;
                int topRank = tripleRanks.get(bestStart + bestLen - 1);
                int expectedExtra = planeRanks; // 带的牌数 = 飞机组数
                int remaining = n - planeRanks * 3;

                if (remaining == 0)
                    return new HandInfo(CardType.AIRPLANE, topRank, planeRanks, cards);
                if (remaining == planeRanks)
                    return new HandInfo(CardType.AIRPLANE_SINGLES, topRank, planeRanks, cards);
                if (remaining == planeRanks * 2)
                    return new HandInfo(CardType.AIRPLANE_PAIRS, topRank, planeRanks, cards);
            }
        }

        return new HandInfo(CardType.INVALID, -1, 0, cards);
    }

    /** 判断能否压过上一手 */
    static boolean canBeat(HandInfo newHand, CardType lastType, int lastRank) {
        if (newHand.type == CardType.INVALID) return false;

        // 火箭通吃
        if (newHand.type == CardType.ROCKET) return true;

        // 炸弹可压非炸弹（除火箭）
        if (newHand.type == CardType.BOMB) {
            if (lastType == CardType.ROCKET) return false;
            if (lastType == CardType.BOMB) return newHand.primaryRank > lastRank;
            return true;
        }

        // 同类型比大小
        if (newHand.type == lastType && newHand.extraCount == (lastType == CardType.STRAIGHT ? 0 : 0)) {
            // 简化：同类型比较主rank
            if (newHand.type == CardType.STRAIGHT || newHand.type == CardType.STRAIGHT_PAIRS
                    || newHand.type == CardType.AIRPLANE || newHand.type == CardType.AIRPLANE_SINGLES
                    || newHand.type == CardType.AIRPLANE_PAIRS) {
                // 这些类型还需要长度相同
                return newHand.primaryRank > lastRank;
            }
            return newHand.primaryRank > lastRank;
        }

        return false;
    }

    private static boolean isStraight(List<Integer> ranks, List<Integer> counts, int len) {
        if (ranks.size() != len) return false;
        if (!counts.stream().allMatch(c -> c == 1)) return false;
        if (!isConsecutive(ranks, len)) return false;
        // 顺子不能含2和王
        for (int r : ranks) if (r >= 12) return false;
        return true;
    }

    private static boolean isConsecutive(List<Integer> sortedRanks, int expectedLen) {
        if (sortedRanks.size() != expectedLen) return false;
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i) != sortedRanks.get(i - 1) + 1) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // AI
    // ═══════════════════════════════════════════════════════════════

    /** 评估手牌强度 (0-100) */
    private static int evaluateHandStrength(List<Integer> hand, boolean isLandlord) {
        int score = 0;
        Map<Integer, Integer> rc = new LinkedHashMap<>();
        for (int c : hand) { rc.merge(gameRank(c), 1, Integer::sum); }

        for (var e : rc.entrySet()) {
            int r = e.getKey(), cnt = e.getValue();
            if (r >= 14) score += cnt * 8;      // 王
            else if (r == 12) score += cnt * 6; // 2
            else if (r == 11) score += cnt * 5; // A
            else if (r >= 8) score += cnt * 3;  // J-K
            else score += cnt;

            if (cnt == 4) score += 20;  // 炸弹
            else if (cnt == 3) score += 6;
        }

        // 顺子、连对潜力
        List<Integer> sortedRanks = new ArrayList<>(rc.keySet());
        sortedRanks.sort(Integer::compareTo);
        int consec = 0;
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i) == sortedRanks.get(i - 1) + 1) consec++;
        }
        score += consec * 3;

        return Math.min(100, score);
    }

    /** AI选择先手出牌 */
    private HandInfo chooseStartingHand(List<Integer> hand, boolean isLandlord) {
        if (hand.size() == 1)
            return analyzeHand(Collections.singletonList(hand.get(0)));

        Map<Integer, List<Integer>> byRank = groupByGameRank(hand);

        // 只剩2张：如果是对子就出，否则出单张
        if (hand.size() == 2) {
            if (byRank.size() == 1 && byRank.values().iterator().next().size() == 2)
                return analyzeHand(hand);
            // 出最小的单张
            int minCard = Collections.min(hand, Comparator.comparingInt(DouDiZhuGameMenu::gameRank));
            return analyzeHand(Collections.singletonList(minCard));
        }

        // 优先出单张最小牌来减少手牌
        // 找单张
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() == 1) {
                return analyzeHand(e.getValue());
            }
        }
        // 找对子
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() == 2) {
                return analyzeHand(e.getValue());
            }
        }
        // 出最小的三带一
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() == 3) {
                List<Integer> triple = e.getValue();
                // 找一个单张带
                for (var e2 : byRank.entrySet()) {
                    if (!e2.getKey().equals(e.getKey()) && e2.getValue().size() == 1) {
                        List<Integer> combo = new ArrayList<>(triple);
                        combo.add(e2.getValue().get(0));
                        return analyzeHand(combo);
                    }
                }
                return analyzeHand(triple);
            }
        }

        // 默认出最小的单张
        int minCard = Collections.min(hand, Comparator.comparingInt(DouDiZhuGameMenu::gameRank));
        return analyzeHand(Collections.singletonList(minCard));
    }

    /** AI选择回应 */
    private HandInfo chooseResponse(List<Integer> hand, CardType lastType, int lastRank, boolean isLandlord) {
        Map<Integer, List<Integer>> byRank = groupByGameRank(hand);

        switch (lastType) {
            case SINGLE -> {
                for (var e : byRank.entrySet()) {
                    if (e.getKey() > lastRank && e.getValue().size() >= 1) {
                        // 不拆炸弹和对子
                        if (e.getValue().size() >= 4) continue;
                        return analyzeHand(Collections.singletonList(e.getValue().get(0)));
                    }
                }
            }
            case PAIR -> {
                for (var e : byRank.entrySet()) {
                    if (e.getKey() > lastRank && e.getValue().size() >= 2) {
                        if (e.getValue().size() >= 4) continue; // 不拆炸弹
                        return analyzeHand(e.getValue().subList(0, 2));
                    }
                }
            }
            case TRIPLE, TRIPLE_ONE, TRIPLE_TWO -> {
                for (var e : byRank.entrySet()) {
                    if (e.getKey() > lastRank && e.getValue().size() >= 3) {
                        List<Integer> cards = new ArrayList<>(e.getValue().subList(0, 3));
                        if (lastType == CardType.TRIPLE_ONE) {
                            for (var e2 : byRank.entrySet()) {
                                if (!e2.getKey().equals(e.getKey()) && e2.getValue().size() >= 1) {
                                    cards.add(e2.getValue().get(0)); break;
                                }
                            }
                        } else if (lastType == CardType.TRIPLE_TWO) {
                            for (var e2 : byRank.entrySet()) {
                                if (!e2.getKey().equals(e.getKey()) && e2.getValue().size() >= 2) {
                                    cards.addAll(e2.getValue().subList(0, 2)); break;
                                }
                            }
                        }
                        return analyzeHand(cards);
                    }
                }
            }
            case STRAIGHT -> {
                // 找更大的顺子
                int len = lastType == CardType.STRAIGHT ? 5 : 0; // 需要从上下文获取长度
                // 简化处理——找炸弹来压
            }
            default -> {}
        }

        // 尝试炸弹
        for (var e : byRank.entrySet()) {
            if (e.getValue().size() == 4 && e.getKey() > lastRank) {
                return analyzeHand(e.getValue());
            }
        }
        // 尝试火箭
        if (byRank.containsKey(14) && byRank.containsKey(15)) {
            List<Integer> rocket = new ArrayList<>();
            rocket.addAll(byRank.get(14));
            rocket.addAll(byRank.get(15));
            return analyzeHand(rocket);
        }

        return null; // 不出
    }

    // ═══════════════════════════════════════════════════════════════
    // 结算
    // ═══════════════════════════════════════════════════════════════

    private void finishGame(int result) {
        phase = PHASE_FINISHED;
        bottomRevealed = true;

        if (betPaid) {
            int totalBet = baseStake * multiplier * bidMultiplier;
            boolean landlordWin = result == RESULT_LANDLORD_WIN;
            boolean playerIsLord = landlordIndex == 0;

            if (landlordWin) {
                if (playerIsLord) {
                    // 玩家是地主，赢了 → 拿回本金 + 两个农民的赌注
                    int win = totalBet * 3; // 本金 + 2个农民
                    addEmeralds(player, win);
                    message = "§a地主获胜！+" + win + " 绿宝石 (底注" + baseStake + "×" + multiplier + "×" + bidMultiplier + ")";
                } else {
                    // 玩家是农民，输了
                    message = "§c" + getPlayerName(landlordIndex) + "（地主）获胜！-" + totalBet + " 绿宝石";
                }
            } else {
                if (playerIsLord) {
                    // 玩家是地主，输了
                    message = "§c农民获胜！你（地主）输了 -" + totalBet + " 绿宝石";
                } else {
                    // 玩家是农民，赢了 → 拿回本金 + 地主的一半
                    int win = totalBet * 2; // 本金 + 地主份额
                    addEmeralds(player, win);
                    message = "§a农民获胜！+" + totalBet + " 绿宝石 (底注" + baseStake + "×" + multiplier + "×" + bidMultiplier + ")";
                }
            }

            betPaid = false;
        }

        sound(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.0F);
        sync();
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    /** 斗地主牌值排序：3=0, 4=1, ..., K=10, A=11, 2=12, 小王=14, 大王=15 */
    static int gameRank(int card) {
        if (card == 53) return 15; // 大王
        if (card == 52) return 14; // 小王
        int r = card % 13;
        if (r == 0) return 11; // A
        if (r == 1) return 12; // 2
        return r - 2;          // 3→0, 4→1, ..., K→10
    }

    /** 斗地主牌值 → 显示符号 */
    static String rankDisplay(int gameRank) {
        String[] R = {"3","4","5","6","7","8","9","10","J","Q","K","A","2"};
        if (gameRank == 14) return "小王";
        if (gameRank == 15) return "大王";
        if (gameRank >= 0 && gameRank < R.length) return R[gameRank];
        return "?";
    }

    static String suitDisplay(int card) {
        if (card >= 52) return "";
        String[] S = {"♠","♥","♦","♣"};
        return S[card / 13];
    }

    static int suitColor(int card) {
        if (card >= 52) return 0xFFCC00FF;
        int suit = card / 13;
        return (suit == 1 || suit == 2) ? 0xFFFF3333 : 0xFF222222;
    }

    private String describeHand(HandInfo info) {
        int gr = info.primaryRank;
        return switch (info.type) {
            case SINGLE -> rankDisplay(gr);
            case PAIR -> "对" + rankDisplay(gr);
            case TRIPLE -> "三" + rankDisplay(gr);
            case TRIPLE_ONE -> "三" + rankDisplay(gr) + "带一";
            case TRIPLE_TWO -> "三" + rankDisplay(gr) + "带二";
            case STRAIGHT -> "顺子(" + info.extraCount + "张)";
            case STRAIGHT_PAIRS -> "连对(" + info.extraCount + "对)";
            case AIRPLANE -> "飞机(" + info.extraCount + "组)";
            case AIRPLANE_SINGLES -> "飞机带单(" + info.extraCount + "组)";
            case AIRPLANE_PAIRS -> "飞机带双(" + info.extraCount + "组)";
            case BOMB -> "炸弹(" + rankDisplay(gr) + ")";
            case ROCKET -> "🚀火箭";
            case FOUR_TWO -> "四带二";
            case FOUR_TWO_PAIRS -> "四带两对";
            default -> "未知";
        };
    }

    private List<Integer> getHand(int idx) {
        return switch (idx) {
            case 0 -> hand0;
            case 1 -> hand1;
            case 2 -> hand2;
            default -> throw new IllegalArgumentException("Invalid player index: " + idx);
        };
    }

    private String getPlayerName(int idx) {
        return switch (idx) {
            case 0 -> player != null ? player.getDisplayName().getString() : "你";
            case 1 -> villager1.name();
            case 2 -> villager2.name();
            default -> "?";
        };
    }

    private Map<Integer, List<Integer>> groupByGameRank(List<Integer> cards) {
        Map<Integer, List<Integer>> map = new LinkedHashMap<>();
        for (int c : cards) {
            map.computeIfAbsent(gameRank(c), k -> new ArrayList<>()).add(c);
        }
        // 按rank排序
        return map;
    }

    static void sortHand(List<Integer> hand) {
        hand.sort(Comparator.comparingInt(DouDiZhuGameMenu::gameRank).reversed());
    }

    /** 从bitmask解析选中的牌 */
    private static List<Integer> cardsFromMask(List<Integer> hand, long mask) {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            if ((mask & (1L << i)) != 0) {
                selected.add(hand.get(i));
            }
        }
        return selected;
    }

    // ═══════════════════════════════════════════════════════════════
    // 牌组
    // ═══════════════════════════════════════════════════════════════

    private void initDeck() {
        deck.clear();
        // 52张标准牌：suit*13 + rank, rank: 0=A, 1=2, 2=3, ..., 12=K
        for (int i = 0; i < 52; i++) deck.add(i);
        deck.add(52); // 小王
        deck.add(53); // 大王
        Collections.shuffle(deck);
    }

    private void dealCards() {
        hand0.clear(); hand1.clear(); hand2.clear(); bottomCards.clear();
        // 每人17张
        for (int i = 0; i < 17; i++) {
            hand0.add(deck.removeLast());
            hand1.add(deck.removeLast());
            hand2.add(deck.removeLast());
        }
        // 3张底牌
        bottomCards.addAll(deck);
        deck.clear();

        sortHand(hand0);
        sortHand(hand1);
        sortHand(hand2);
    }

    // ═══════════════════════════════════════════════════════════════
    // 绿宝石
    // ═══════════════════════════════════════════════════════════════

    private static int countEmeralds(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(Items.EMERALD)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static void removeEmeralds(Player p, int c) {
        for (int i = 0; i < p.getInventory().getContainerSize() && c > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) { int r = Math.min(c, s.getCount()); s.shrink(r); c -= r; }
        }
    }

    private static void addEmeralds(Player p, int c) {
        if (c <= 0) return;
        ItemStack e = new ItemStack(Items.EMERALD, c);
        if (!p.getInventory().add(e)) p.spawnAtLocation((ServerLevel) p.level(), e);
    }

    // ═══════════════════════════════════════════════════════════════

    private void sound(SoundEvent s, float v, float p) {
        if (player != null) player.level().playSound(null, player.blockPosition(), s, SoundSource.PLAYERS, v, p);
    }

    /** 同步游戏状态到客户端 */
    public void sync() {
        if (player instanceof ServerPlayer sp) {
            var p2 = villager1;
            var p3 = villager2;
            sp.connection.send(new DouDiZhuSyncPayload(
                    phase,
                    new ArrayList<>(hand0),
                    hand1.size(),
                    hand2.size(),
                    bottomRevealed ? new ArrayList<>(bottomCards) : new ArrayList<>(),
                    landlordIndex,
                    currentPlayerIdx,
                    new ArrayList<>(lastPlayedCards),
                    lastPlayedBy,
                    lastPlayedType != CardType.INVALID ? describeHand(
                            new HandInfo(lastPlayedType, lastPlayedRank, lastPlayedExtra, lastPlayedCards)) : "",
                    message,
                    p2.name(), p3.name(),
                    p2.budget(), p3.budget(),
                    baseStake, multiplier,
                    phase == PHASE_FINISHED ? (landlordIndex == 0 && hand0.isEmpty() ? RESULT_LANDLORD_WIN :
                            (landlordIndex != 0 && (hand1.isEmpty() || hand2.isEmpty()) ? RESULT_FARMER_WIN : RESULT_NONE)) : RESULT_NONE,
                    bidMultiplier,
                    bottomRevealed,
                    lastActorIdx
            ));
            lastActorIdx = -1; // 重置
        }
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide()) {
            // 回写村民预算
            writeBackVillagerBudget(villager1);
            writeBackVillagerBudget(villager2);
            // 如果游戏还在进行，视为玩家逃跑
            if ((phase == PHASE_BIDDING || phase == PHASE_PLAYING) && betPaid) {
                // 没收赌注
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("§c你逃跑了！赌注被没收"));
                }
            }
        }
    }

    private void writeBackVillagerBudget(VillagerInfo vi) {
        if (vi == null || vi.entityId() < 0 || player == null) return;
        if (player.level().getEntity(vi.entityId()) instanceof net.minecraft.world.entity.npc.villager.AbstractVillager v) {
            v.setData(com.hasoook.hasoook.component.ModAttachments.NITWIT_BUDGET.get(), vi.budget());
        }
    }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}

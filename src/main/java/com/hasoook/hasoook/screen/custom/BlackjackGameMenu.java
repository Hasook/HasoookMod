package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.item.custom.PokerItem;
import com.hasoook.hasoook.network.payload.BlackjackSyncPayload;
import com.hasoook.hasoook.screen.ModMenuTypes;
import com.hasoook.hasoook.util.TickScheduler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlackjackGameMenu extends AbstractContainerMenu {

    public static final int PHASE_MULTIPLIER = 0;
    public static final int PHASE_PLAYING = 1;
    public static final int PHASE_FINISHED = 2;

    public static final int RESULT_NONE = 0, RESULT_WIN = 1, RESULT_LOSE = 2, RESULT_PUSH = 3;
    public static final int[] MULTIPLIERS = {1, 2, 4, 8};

    public static final int ACTION_SELECT_MULTIPLIER = 0, ACTION_HIT = 1, ACTION_STAND = 2;
    public static final int ACTION_SURRENDER = 3, ACTION_SCREEN_READY = 4, ACTION_DOUBLE_DOWN = 5;
    public static final int ACTION_CHEAT = 6;
    public static final int ACTION_INVITE_DOUDIZHU = 7;

    private static final int DEALER_DRAW_DELAY = 20; // 1秒/张

    private final Player player;
    private final int baseStake;
    private int nitwitBudget;          // 傻子库存，-1=无限
    private final int villagerId;      // 用于回写库存

    private int phase = PHASE_MULTIPLIER;
    private final List<Integer> deck = new ArrayList<>();
    private final List<Integer> playerCards = new ArrayList<>();
    private final List<Integer> dealerCards = new ArrayList<>();
    private int multiplier = 0;
    private boolean dealerHidden = true;
    private int result = RESULT_NONE;
    private String message = "";
    private boolean betPlaced = false;
    private boolean dealerDrawing = false;
    private boolean canDoubleDown = false;
    private boolean dealerDoubled = false;
    private boolean playerIsDealer = false;  // 玩家坐庄
    private boolean dealerIsNitwit = false;   // 村民是傻子
    private int cheatAttempts = 0;            // 本局出千次数

    // ── 客户端 ──
    public BlackjackGameMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenuTypes.BLACKJACK_GAME_MENU.get(), containerId);
        this.player = inv.player; this.baseStake = 0;
        this.nitwitBudget = -1; this.villagerId = -1;
    }

    // ── 服务端 ──
    public BlackjackGameMenu(int containerId, Inventory inv, Player player, String dealerName,
                              int baseStake, boolean dealerIsNitwit, int nitwitBudget, int villagerId) {
        super(ModMenuTypes.BLACKJACK_GAME_MENU.get(), containerId);
        this.player = player; this.baseStake = baseStake;
        this.dealerIsNitwit = dealerIsNitwit;
        this.nitwitBudget = nitwitBudget; this.villagerId = villagerId;
    }

    public void handleAction(int action, int data) {
        message = ""; // 清空旧消息
        switch (action) {
            case ACTION_SELECT_MULTIPLIER -> selectMultiplier(data);
            case ACTION_HIT -> playerHit();
            case ACTION_STAND -> playerStand();
            case ACTION_SURRENDER -> playerSurrender();
            case ACTION_DOUBLE_DOWN -> playerDoubleDown();
            case ACTION_CHEAT -> playerCheat();
            case ACTION_INVITE_DOUDIZHU -> inviteDouDiZhu();
            case ACTION_SCREEN_READY -> sync();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 倍率选择 — bet = baseStake * multiplier
    // ═══════════════════════════════════════════════════════════════

    private void selectMultiplier(int mult) {
        if (phase != PHASE_MULTIPLIER) return;
        boolean ok = false;
        for (int m : MULTIPLIERS) if (m == mult) { ok = true; break; }
        if (!ok) return;

        // 消耗扑克牌 1 点耐久
        for (var hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof PokerItem) {
                held.hurtAndBreak(1, player,
                        hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                break;
            }
        }

        int bet = baseStake * mult;
        if (countEmeralds(player) < bet) {
            message = "§c你需要 " + bet + " 颗绿宝石！（底注" + baseStake + " ×" + mult + "）";
            sync();
            return;
        }
        removeEmeralds(player, bet);

        this.multiplier = mult;
        this.betPlaced = true;
        sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
        startGame();
    }

    // ═══════════════════════════════════════════════════════════════
    // 发牌
    // ═══════════════════════════════════════════════════════════════

    private void startGame() {
        phase = PHASE_PLAYING;
        playerCards.clear(); dealerCards.clear(); initDeck();
        playerCards.add(draw()); dealerCards.add(draw());
        playerCards.add(draw()); dealerCards.add(draw());
        canDoubleDown = true;
        dealerDoubled = false;
        cheatAttempts = 0;
        playerIsDealer = player.level().getRandom().nextFloat() < 0.33f;
        sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8F, 1.0F);
        if (playerIsDealer) {
            message = "§b你坐庄！村民先手要牌...";
            // 不翻牌，村民要牌过程保持暗牌，等玩家停牌才翻
            dealerDrawing = true;
            sync();
            schedDealerPre();
        } else {
            sync();
        }
    }

    // ── 玩家坐庄：村民先手逐张要牌 ──

    private void schedDealerPre() {
        if (player.level() instanceof ServerLevel sl)
            TickScheduler.schedule(sl, DEALER_DRAW_DELAY, this::dealerPreOne);
    }

    private void dealerPreOne() {
        if (phase != PHASE_PLAYING || !dealerDrawing) return;
        int s = score(dealerCards);
        if (s < dealerStopAt()) {
            dealerCards.add(draw());
            sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.7F, 0.9F);
            sync();
            if (score(dealerCards) < 17) schedDealerPre();
            else dealerPreDone();
        } else {
            dealerPreDone();
        }
    }

    private void dealerPreDone() {
        dealerDrawing = false;
        int ds = score(dealerCards);
        if (ds > 21) {
            bustSound();
            finish(RESULT_WIN, "村民爆牌");
        } else {
            message = "§b村民停牌，轮到你了！";
            sync();
        }
    }

    private void playerHit() {
        if (phase != PHASE_PLAYING) return;
        canDoubleDown = false;
        playerCards.add(draw());
        sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.7F, 0.8F);
        int s = score(playerCards);
        if (s > 21) {
            dealerHidden = false;
            bustSound();
            finish(RESULT_LOSE, "爆牌");
            return;
        }
        sync();
    }

    // ═══════════════════════════════════════════════════════════════
    // 加倍 — 再押 baseStake，倍率翻倍
    // ═══════════════════════════════════════════════════════════════

    private void playerDoubleDown() {
        if (phase != PHASE_PLAYING || !canDoubleDown) return;
        int extra = baseStake * multiplier; // 追加押注
        if (countEmeralds(player) < extra) {
            message = "§c你需要 " + extra + " 颗绿宝石才能加倍！";
            sync(); return;
        }
        removeEmeralds(player, extra);
        multiplier *= 2;
        canDoubleDown = false;
        sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
        sync();
    }

    // ═══════════════════════════════════════════════════════════════
    // 出千 — 需作弊附魔，改为随机点数，概率被发现
    // ═══════════════════════════════════════════════════════════════

    private boolean hasCheatEnchant() {
        for (var hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof PokerItem) {
                var ench = held.getEnchantments();
                var lookup = player.level().registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                if (ench.getLevel(lookup.getOrThrow(
                        com.hasoook.hasoook.enchantment.ModEnchantments.CHEATING)) > 0)
                    return true;
            }
        }
        return false;
    }

    private void playerCheat() {
        if (phase != PHASE_PLAYING || playerCards.size() < 2) return;
        if (!hasCheatEnchant()) return;

        cheatAttempts++;
        float catchChance = cheatAttempts <= 1 ? 0 : Math.min(0.10f + (cheatAttempts - 2) * 0.15f, 0.85f);
        boolean caught = player.level().getRandom().nextFloat() < catchChance;

        if (caught) {
            dealerHidden = false;
            player.hurt(player.damageSources().magic(), 4.0f);
            if (player instanceof ServerPlayer sp)
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
            // 双倍惩罚：额外扣一次押注
            removeEmeralds(player, baseStake * multiplier);
            multiplier *= 2;
            finish(RESULT_LOSE, "出千被抓");
        } else {
            int idx = 0;
            int oldCard = playerCards.get(idx);
            int oldRank = oldCard % 13;
            int suit = oldCard / 13;
            int newRank;
            do { newRank = 1 + player.level().getRandom().nextInt(9); } while (newRank == oldRank);
            playerCards.set(idx, suit * 13 + newRank);
            sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.5F, 1.5F);
            message = "§e偷偷换了一张牌...";
            sync();
        }
    }

    private void playerStand() {
        if (phase != PHASE_PLAYING || dealerDrawing) return;
        canDoubleDown = false;
        if (playerIsDealer) {
            revealWithDrum();
            return;
        }
        // 村民加倍：庄家明牌≥7时25%概率翻倍押注
        if (!dealerDoubled && !dealerCards.isEmpty()) {
            int visible = dealerCards.get(1) % 13;
            int v = (visible == 0 ? 11 : visible >= 10 ? 10 : visible + 1);
            int extra = baseStake * multiplier;
            if (v >= 7 && countEmeralds(player) >= extra
                    && player.level().getRandom().nextFloat() < 0.25f) {
                removeEmeralds(player, extra);
                dealerDoubled = true;
                multiplier *= 2;
                message = "§6村民加倍！追加" + extra + "，×" + multiplier;
            }
        }
        dealerDrawing = true;
        sync(); schedDealer();
    }

    private void playerSurrender() {
        if (phase != PHASE_PLAYING) return;
        dealerDrawing = false; canDoubleDown = false; dealerHidden = false;
        sound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
        finish(RESULT_LOSE, "认输");
    }

    // ═══════════════════════════════════════════════════════════════
    // 庄家
    // ═══════════════════════════════════════════════════════════════

    private int dealerStopAt() {
        // 根据玩家已要牌的数量调整策略（不直接看点数，只看牌数）
        int cards = playerCards.size();
        if (cards <= 2) return 18;          // 玩家只有2张，手牌可能很强，庄家激进
        if (cards >= 5) return 15;          // 玩家牌多接近爆牌，庄家保守
        return dealerIsNitwit ? 19 : 17;    // 正常策略（傻子激进）
    }

    private void schedDealer() {
        if (player.level() instanceof ServerLevel sl)
            TickScheduler.schedule(sl, DEALER_DRAW_DELAY, this::dealerOne);
    }

    private void dealerOne() {
        if (phase != PHASE_PLAYING || !dealerDrawing) return;
        int s = score(dealerCards);
        if (s < dealerStopAt()) {
            dealerCards.add(draw()); sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.7F, 0.9F); sync();
            if (score(dealerCards) > 21) {
                dealerDrawing = false; dealerHidden = false;
                bustSound();
                endDealer();
            } else if (score(dealerCards) < 17) {
                schedDealer();
            } else {
                dealerDrawing = false;
                revealWithDrum();
            }
        } else {
            dealerDrawing = false;
            revealWithDrum();
        }
    }

    private void revealWithDrum() {
        schedulePling(2, 0.5F, 0.6F);
    }

    private void schedulePling(int delay, float vol, float pitch) {
        if (player.level() instanceof ServerLevel sl)
            TickScheduler.schedule(sl, delay, () -> {
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), vol, pitch);
                if (pitch < 1.6F)
                    schedulePling(2, vol + 0.04F, pitch + 0.10F);
                else
                    TickScheduler.schedule((ServerLevel) player.level(), 4, () -> {
                        dealerHidden = false;
                        sync();
                        TickScheduler.schedule((ServerLevel) player.level(), 4, this::endDealer);
                    });
            });
    }

    private void endDealer() {
        int ds = score(dealerCards), ps = score(playerCards);
        if (ds > 21)       finish(RESULT_WIN,  "庄家爆牌，你赢了");
        else if (ds > ps)  finish(RESULT_LOSE, "你输了");
        else if (ds < ps)  finish(RESULT_WIN,  "你赢了");
        else               finish(RESULT_PUSH, "");
    }

    // ═══════════════════════════════════════════════════════════════
    // 结算 — 输赢都是 baseStake * multiplier
    // ═══════════════════════════════════════════════════════════════

    private void finish(int r, String reason) {
        phase = PHASE_FINISHED; result = r;
        if (betPlaced) {
            int amount = baseStake * multiplier;
            if (r == RESULT_WIN) {
                int win = Math.min(amount, nitwitBudget >= 0 ? nitwitBudget : Integer.MAX_VALUE);
                addEmeralds(player, amount + win);
                if (nitwitBudget >= 0) { nitwitBudget -= win; if (nitwitBudget < 0) nitwitBudget = 0; }
                String prefix = reason.isEmpty() ? "你赢了" : reason;
                if (win < amount) {
                    String itemsMsg = repayWithGoods(amount - win);
                    if (!itemsMsg.isEmpty())
                        message = "§a" + prefix + "！+" + win + "绿宝石 +" + itemsMsg;
                    else
                        message = "§a" + prefix + "！+" + win + "（应得" + amount + "）";
                } else {
                    message = "§a" + prefix + "！+" + amount;
                }
                sound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            } else if (r == RESULT_PUSH) {
                addEmeralds(player, amount);
                message = "平局！返还 " + amount;
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            } else {
                if (nitwitBudget >= 0) nitwitBudget += amount;
                String prefix = reason.isEmpty() ? "你输了" : reason;
                message = "§c" + prefix + "！-" + amount;
                sound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
            }
            betPlaced = false;
        }
        sync();
    }

    // ── 村民抵债：拿职业物品 ──

    /// 用村民交易物品抵债，返回物品描述字符串
    private String repayWithGoods(int debt) {
        if (villagerId < 0 || !(player.level().getEntity(villagerId) instanceof Villager v))
            return "";

        var offers = v.getOffers();
        if (offers.isEmpty()) return "";

        record Trade(int cost, ItemStack item) {}
        var trades = new java.util.ArrayList<Trade>();
        for (var o : offers) {
            ItemStack result = o.getResult();
            int cost = o.getBaseCostA().getCount();
            if (!result.isEmpty() && cost > 0)
                trades.add(new Trade(cost, result.copy()));
        }
        if (trades.isEmpty()) return "";

        // 统计每种物品给了多少个
        var items = new java.util.HashMap<Item, Integer>();
        var names = new java.util.HashMap<Item, String>();
        int maxItems = 12, given = 0;
        var rand = player.level().getRandom();

        while (debt > 0 && given < maxItems) {
            Trade t = trades.get(rand.nextInt(trades.size()));
            int count = Math.min(Math.max(1, debt / t.cost), t.item.getMaxStackSize());
            ItemStack stack = t.item.copy();
            stack.setCount(count);
            player.getInventory().add(stack);
            if (!stack.isEmpty())
                player.spawnAtLocation((ServerLevel) player.level(), stack);

            items.merge(t.item.getItem(), count, Integer::sum);
            names.putIfAbsent(t.item.getItem(), t.item.getDisplayName().getString());
            debt -= count * t.cost;
            given++;
        }

        if (items.isEmpty()) return "";
        var sb = new StringBuilder();
        for (var e : items.entrySet())
            sb.append(e.getValue()).append("个").append(names.get(e.getKey())).append(" ");
        return sb.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════
    // 牌组
    // ═══════════════════════════════════════════════════════════════

    private void initDeck() { deck.clear(); for (int i = 0; i < 52; i++) deck.add(i); Collections.shuffle(deck); }
    private int draw() { if (deck.isEmpty()) initDeck(); return deck.removeLast(); }

    public static int score(List<Integer> cards) {
        int s = 0, a = 0;
        for (int c : cards) { int r = c % 13; if (r == 0) { a++; s += 11; } else if (r >= 10) s += 10; else s += r + 1; }
        while (s > 21 && a > 0) { s -= 10; a--; }
        return s;
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
        ItemStack e = new ItemStack(Items.EMERALD, c);
        if (!p.getInventory().add(e)) p.spawnAtLocation((ServerLevel) p.level(), e);
    }

    // ═══════════════════════════════════════════════════════════════

    private void sound(SoundEvent s, float v, float p) { player.level().playSound(null, player.blockPosition(), s, SoundSource.PLAYERS, v, p); }

    private void bustSound() { sound(SoundEvents.GENERIC_EXPLODE.value(), 0.3F, 0.7F); }

    private void sync() {
        if (player instanceof ServerPlayer sp) sp.connection.send(new BlackjackSyncPayload(
                phase, new ArrayList<>(playerCards), new ArrayList<>(dealerCards),
                dealerHidden, multiplier, score(playerCards), score(dealerCards),
                result, message, canDoubleDown, baseStake, playerIsDealer, nitwitBudget, dealerDrawing));
    }

    // ═══════════════════════════════════════════════════════════════
    // 邀请斗地主
    // ═══════════════════════════════════════════════════════════════

    private void inviteDouDiZhu() {
        if (phase != PHASE_MULTIPLIER || !(player instanceof ServerPlayer sp)) return;

        // 在玩家16格范围内找有钱的村民
        var level = sp.level();
        var pos = sp.blockPosition();
        var aabb = new AABB(pos).inflate(16);
        List<AbstractVillager> candidates = new ArrayList<>();

        for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.AbstractVillager.class,
                aabb, v -> true)) {
            int budget = entity.getData(ModAttachments.NITWIT_BUDGET.get());
            if (budget > 0) {
                candidates.add(entity);
            }
        }

        if (candidates.size() < 2) {
            message = "§c16格内至少需要2个有钱的村民才能斗地主！（找到" + candidates.size() + "个）";
            sync();
            return;
        }

        // 随机挑2个村民
        var rand = sp.level().getRandom();
        Collections.shuffle(candidates);
        var v1 = candidates.get(0);
        var v2 = candidates.get(1);

        int budget1 = v1.getData(ModAttachments.NITWIT_BUDGET.get());
        int budget2 = v2.getData(ModAttachments.NITWIT_BUDGET.get());
        long day = sp.level().getDayTime() / 24000L;

        // 刷新每日预算
        for (var v : new AbstractVillager[]{v1, v2}) {
            long lastDay = v.getPersistentData().getLong("nitwit_day").orElse(-1L);
            if (lastDay != day && v instanceof Villager vv) {
                int levelVal = vv.getVillagerData().level();
                v.setData(ModAttachments.NITWIT_BUDGET.get(), levelVal * (20 + rand.nextInt(31)));
                v.getPersistentData().putLong("nitwit_day", day);
            }
        }
        budget1 = v1.getData(ModAttachments.NITWIT_BUDGET.get());
        budget2 = v2.getData(ModAttachments.NITWIT_BUDGET.get());

        boolean nitwit1 = false, nitwit2 = false;
        if (v1 instanceof Villager vv1) nitwit1 = vv1.getVillagerData().profession().toString().contains("nitwit");
        if (v2 instanceof Villager vv2) nitwit2 = vv2.getVillagerData().profession().toString().contains("nitwit");

        // 底注
        int baseStake;
        if (v1 instanceof Villager vv1) {
            int lv = vv1.getVillagerData().level();
            baseStake = lv * (2 + rand.nextInt(3));
        } else {
            baseStake = 2 + rand.nextInt(7);
        }

        final int finalBudget1 = budget1;
        final int finalBudget2 = budget2;
        final boolean fNitwit1 = nitwit1;
        final boolean fNitwit2 = nitwit2;
        final int fBaseStake = baseStake;

        // 关闭当前菜单并打开斗地主
        sp.closeContainer();
        TickScheduler.schedule((ServerLevel) level, 2, () -> {
            sp.openMenu(new SimpleMenuProvider(
                    (containerId, inv, p) -> new DouDiZhuGameMenu(containerId, inv, sp,
                            new DouDiZhuGameMenu.VillagerInfo(
                                    v1.getDisplayName().getString(), v1.getId(), finalBudget1, fNitwit1),
                            new DouDiZhuGameMenu.VillagerInfo(
                                    v2.getDisplayName().getString(), v2.getId(), finalBudget2, fNitwit2),
                            fBaseStake),
                    Component.translatable("gui.hasoook.doudizhu")
            ));
        });
    }

    @Override public void removed(Player p) {
        super.removed(p);
        // 回写傻子库存到villager
        if (!p.level().isClientSide() && nitwitBudget >= 0 && villagerId >= 0) {
            var entity = p.level().getEntity(villagerId);
            if (entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager v) {
                v.setData(com.hasoook.hasoook.component.ModAttachments.NITWIT_BUDGET.get(), nitwitBudget);
            }
        }
        if (phase == PHASE_PLAYING && betPlaced && !p.level().isClientSide()) playerSurrender();
    }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return true; }
}

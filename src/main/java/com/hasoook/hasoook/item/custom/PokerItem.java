package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.entity.custom.CardProjectile;
import com.hasoook.hasoook.screen.custom.BlackjackGameMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class PokerItem extends Item {

    private static final String TAG_CHEATING_SUITS = "CheatingSuits";

    public PokerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack triggerStack = player.getItemInHand(hand);
        if (!hasThrowEnchant(triggerStack, player)) return InteractionResult.PASS;

        // 客户端：挥动手臂动画
        if (level.isClientSide()) {
            player.swing(hand);
            // 主手触发时副手也挥动
            if (hand == InteractionHand.MAIN_HAND) {
                ItemStack offStack = player.getOffhandItem();
                if (offStack.getItem() instanceof PokerItem && hasThrowEnchant(offStack, player))
                    player.swing(InteractionHand.OFF_HAND);
            }
            return InteractionResult.SUCCESS;
        }

        // ── 服务端 ──
        boolean isMain = (hand == InteractionHand.MAIN_HAND);
        // 检查副手
        ItemStack offStack = player.getOffhandItem();
        boolean offReady = offStack.getItem() instanceof PokerItem && hasThrowEnchant(offStack, player);
        boolean dualWield = isMain && offReady;

        // 主手发射
        ChainData cd = calcChain(triggerStack);
        fireCards(level, player, triggerStack, cd, dualWield ? -0.15 : 0);
        if (cd.cheat > 0) consumeCards(triggerStack, cd.chain);

        // 副手独立发射 (用自己的花色队列)
        if (dualWield) {
            ChainData offCd = calcChain(offStack);
            fireCards(level, player, offStack, offCd, 0.15);
            if (offCd.cheat > 0) consumeCards(offStack, offCd.chain);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CHEATING, stack) <= 0) return;
        if (ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CARD_THROW, stack) <= 0) return;

        int[] suits = getSuits(stack);
        MutableComponent line = Component.literal("[ ").withColor(0xFFAAAAAA);
        for (int i = 0; i < 4; i++) {
            if (i > 0) line.append(Component.literal("  "));
            int s = suits[i];
            // 深色底可见的亮色: 黑→天蓝, 红→亮红
            int cs = suitColor(s) == 0xFF222222 ? 0xFF55AAFF : 0xFFFF4444;
            line.append(Component.literal(suitSymbol(s)).withColor(cs));
        }
        line.append(Component.literal(" ]").withColor(0xFFAAAAAA));
        tooltipAdder.accept(line);
    }

    /** 计算出千链牌数据 */
    private record ChainData(int cheat, int chain, int multi, boolean bomb, int total) {}
    private ChainData calcChain(ItemStack stack) {
        int cheat = ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.CHEATING, stack);
        int multi = ModEnchantmentHelper.getEnchantmentLevel(Enchantments.MULTISHOT, stack);
        int chain = 1;
        if (cheat > 0) {
            int[] suits = getOrInitSuits(stack);
            int suit = suits[0];
            for (int i = 1; i < 4; i++) {
                if (suits[i] == suit) chain++; else break;
            }
        }
        boolean bomb = (chain == 4);
        return new ChainData(cheat, chain, multi, bomb, chain + multi);
    }

    /** 发射牌, curve<0=左弧线, curve>0=右弧线 */
    private void fireCards(Level level, Player player, ItemStack stack, ChainData cd, double curve) {
        float spread = cd.total > 1 ? 5.0F : 0.0F;
        for (int i = 0; i < cd.total; i++) {
            CardProjectile projectile = new CardProjectile(player, level);
            projectile.setPos(player.getEyePosition());
            float yRot = player.getYRot() + (cd.total > 1 ? (i - (cd.total - 1) / 2.0F) * spread : 0.0F);
            projectile.shootFromRotation(player, player.getXRot(), yRot, 0.0F, 2.5F, 0.5F);
            if (curve != 0) {
                net.minecraft.world.phys.Vec3 forward = projectile.getDeltaMovement().normalize();
                net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-forward.z, 0, forward.x).scale(curve * 2.5);
                projectile.setDeltaMovement(projectile.getDeltaMovement().add(side));
                projectile.setCurve(forward, 0.5, 1);
            }
            projectile.setDamage(2.0F);
            if (cd.cheat > 0) projectile.setHasCheating(true);
            if (cd.bomb) projectile.setBomb(true);
            level.addFreshEntity(projectile);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.6F + cd.total * 0.1F, 1.2F);
        if (!player.getAbilities().instabuild)
            stack.hurtAndBreak(1, player,
                    stack == player.getMainHandItem() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    // ═══════════════════════════════════════════════════════════════
    // 出千花色存储
    // ═══════════════════════════════════════════════════════════════

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 初始化或读取4个花色 */
    private static int[] getOrInitSuits(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        int[] suits = tag.getIntArray(TAG_CHEATING_SUITS).orElse(new int[0]);
        if (suits.length != 4) {
            suits = new int[4];
            RandomSource rand = RandomSource.create();
            for (int i = 0; i < 4; i++) suits[i] = rand.nextInt(4);
            tag.putIntArray(TAG_CHEATING_SUITS, suits);
            saveTag(stack, tag);
        }
        return suits;
    }

    /** 获取全部4个花色 */
    public static int[] getSuits(ItemStack stack) {
        return getOrInitSuits(stack);
    }

    /** 获得花色符号 */
    public static String suitSymbol(int suit) {
        return switch (suit) {
            case 0 -> "♠";
            case 1 -> "♥";
            case 2 -> "♦";
            case 3 -> "♣";
            default -> "?";
        };
    }

    /** 花色颜色 */
    public static int suitColor(int suit) {
        return (suit == 1 || suit == 2) ? 0xFFFF3333 : 0xFF222222;
    }

    /** 消耗前 count 张牌，后面的牌前移，末尾补新花色 */
    private static void consumeCards(ItemStack stack, int count) {
        CompoundTag tag = getOrCreateTag(stack);
        int[] suits = tag.getIntArray(TAG_CHEATING_SUITS).orElse(new int[0]);
        if (suits.length != 4) return;
        RandomSource rand = RandomSource.create();
        // 前移
        for (int i = 0; i < 4 - count; i++)
            suits[i] = suits[i + count];
        // 末尾补新
        for (int i = 4 - count; i < 4; i++)
            suits[i] = rand.nextInt(4);
        tag.putIntArray(TAG_CHEATING_SUITS, suits);
        saveTag(stack, tag);
    }

    // ═══════════════════════════════════════════════════════════════

    private static boolean hasThrowEnchant(ItemStack stack, Player player) {
        var ench = stack.getEnchantments();
        var lookup = player.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        return ench.getLevel(lookup.getOrThrow(
                com.hasoook.hasoook.enchantment.ModEnchantments.CARD_THROW)) > 0;
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof AbstractVillager villager)) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof PokerItem)) return;

        event.setCanceled(true);

        RandomSource rand = villager.level().getRandom();
        int baseStake;
        boolean nitwit = false;
        int level = 1;

        if (villager instanceof Villager v) {
            nitwit = v.getVillagerData().profession().toString().contains("nitwit");
            level = v.getVillagerData().level();
            baseStake = level * (2 + rand.nextInt(3));
        } else {
            baseStake = 2 + rand.nextInt(7);
        }

        int budget = villager.getData(com.hasoook.hasoook.component.ModAttachments.NITWIT_BUDGET.get());
        long day = villager.level().getDayTime() / 24000L;
        long lastDay = villager.getPersistentData().getLong("nitwit_day").orElse(-1L);
        if (lastDay != day) {
            budget = level * (20 + rand.nextInt(31));
            villager.getPersistentData().putLong("nitwit_day", day);
        }
        if (budget <= 0) {
            player.sendSystemMessage(Component.literal("§c这个村民已经没钱了，明天再来吧！"));
            return;
        }

        final int stake = baseStake;
        final boolean isNitwit = nitwit;
        final int finalBudget = budget;
        final int vId = villager.getId();

        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new BlackjackGameMenu(containerId, inv, p,
                        villager.getDisplayName().getString(), stake, isNitwit, finalBudget, vId),
                Component.translatable("gui.hasoook.blackjack")
        ));
    }
}

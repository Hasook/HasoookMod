package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.custom.ArmorStandSwordProjectile;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ArmorStandSwordItem extends Item {

    private static final int SLOT_COUNT = 4;

    public ArmorStandSwordItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // 动态属性：伤害 = (1 + (护甲值 + 韧性) × 0.5) × (1 + 保护等级 × 4%)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        float bonus = 0;
        int protLevel = 0;
        for (ItemStack stored : readItems(stack)) {
            if (stored.isEmpty()) continue;
            ItemAttributeModifiers mods = stored.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (mods == null) {
                mods = stored.getItem().components()
                        .get(DataComponents.ATTRIBUTE_MODIFIERS);
            }
            if (mods != null) {
                for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                    if (entry.attribute().equals(Attributes.ARMOR)
                            || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
                        bonus += (float) entry.modifier().amount();
                    }
                }
            }
            // 累加保护附魔等级
            ItemEnchantments ench = stored.get(DataComponents.ENCHANTMENTS);
            if (ench != null) {
                for (var entry : ench.entrySet()) {
                    if (entry.getKey().is(Enchantments.PROTECTION)) {
                        protLevel += entry.getValue();
                    }
                }
            }
        }

        float damage = (1.0f + bonus * 0.5f) * (1.0f + protLevel * 0.05f);
        int count = countStored(stack);
        // 每件装备 -0.15 攻速
        float speed = -2.4f + count * (-0.2f);

        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, speed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // 攻击时消耗装备耐久
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void hurtEnemy(ItemStack swordStack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide()) return;

        NonNullList<ItemStack> items = readItems(swordStack);
        boolean changed = false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack armor = items.get(i);
            if (armor.isEmpty() || !armor.isDamageableItem()) continue;

            int dmg = armor.getDamageValue() + 1;
            if (dmg >= armor.getMaxDamage()) {
                items.set(i, ItemStack.EMPTY);
                attacker.playSound(SoundEvents.ITEM_BREAK.value(), 0.8f, 1.0f);
            } else {
                armor.setDamageValue(dmg);
                items.set(i, armor);
            }
            changed = true;
        }

        if (changed) {
            writeItems(swordStack, items);
            if (attacker instanceof Player p) broadcast(p);
        }
    }

    /**
     * 投掷时消耗剑内装备的耐久，像三叉戟投掷会减少耐久一样。
     * 每件可损坏的装备各减 1 点耐久，耐久耗尽则销毁装备。
     */
    private void damageEquipmentOnThrow(ItemStack swordStack, LivingEntity entity) {
        NonNullList<ItemStack> items = readItems(swordStack);
        boolean changed = false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack armor = items.get(i);
            if (armor.isEmpty() || !armor.isDamageableItem()) continue;

            int dmg = armor.getDamageValue() + 1;
            if (dmg >= armor.getMaxDamage()) {
                items.set(i, ItemStack.EMPTY);
                entity.playSound(SoundEvents.ITEM_BREAK.value(), 0.8f, 1.0f);
            } else {
                armor.setDamageValue(dmg);
                items.set(i, armor);
            }
            changed = true;
        }

        if (changed) {
            writeItems(swordStack, items);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 投掷能力：装备鞘翅后可像三叉戟一样扔出
    // ═══════════════════════════════════════════════════════════════

    /**
     * 检查剑上是否装备了鞘翅（slot 1 = 胸甲/身体槽位）
     */
    private static boolean hasElytra(ItemStack swordStack) {
        NonNullList<ItemStack> items = readItems(swordStack);
        ItemStack chestItem = items.get(1); // slot 1 = CHEST / BODY
        return !chestItem.isEmpty() && chestItem.getItem() == net.minecraft.world.item.Items.ELYTRA;
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player,
                                          @NonNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 只有装备了鞘翅才能投掷
        if (!hasElytra(stack)) {
            return InteractionResult.PASS;
        }

        if (stack.nextDamageWillBreak()) {
            return InteractionResult.FAIL;
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level,
                                @NonNull LivingEntity entity, int timeLeft) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return super.releaseUsing(stack, level, entity, timeLeft);
        }

        // 必须装备鞘翅
        if (!hasElytra(stack)) {
            return super.releaseUsing(stack, level, entity, timeLeft);
        }

        // 蓄力至少 10 ticks（0.5 秒）
        int duration = this.getUseDuration(stack, entity) - timeLeft;
        if (duration < 10) {
            return super.releaseUsing(stack, level, entity, timeLeft);
        }

        // 计算投掷伤害：基于当前剑的攻击力（和手持攻击时一致）
        float thrownDamage;
        ItemAttributeModifiers mods = this.getDefaultAttributeModifiers(stack);
        thrownDamage = mods.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .findFirst()
                .map(entry -> (float) entry.modifier().amount())
                .orElse(1.0f);

        // 装备越多 → 投掷动能越低（每件装备 -15% 速度，最低 25%）
        int equipmentCount = countStored(stack);
        float speedPower = 2.5F * Math.max(0.25F, 1.2F - equipmentCount * 0.1F);

        // 非创造模式：先消耗装备耐久（像三叉戟一样），再生成弹射物
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            damageEquipmentOnThrow(stack, entity); // 投掷时剑内装备各减 1 耐久
        }

        Projectile.spawnProjectileFromRotation(
                (lvl, shooter, item) -> {
                    ArmorStandSwordProjectile projectile =
                            new ArmorStandSwordProjectile(shooter, lvl, item);
                    projectile.setDamage(thrownDamage);
                    projectile.setCritArrow(true);
                    return projectile;
                },
                serverLevel,
                stack,
                entity,
                0.0F,       // z (俯仰角偏移)
                speedPower, // velocity (投掷速度)
                1.0F        // inaccuracy (散布)
        );

        // 非创造模式消耗物品
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return super.releaseUsing(stack, level, entity, timeLeft);
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return hasElytra(stack) ? ItemUseAnimation.TRIDENT : ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return 72000;
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品栏交互：剑 → 格子（模仿 BundleItem.overrideStackedOnOther）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean overrideStackedOnOther(ItemStack swordStack, Slot slot,
                                          ClickAction clickAction, Player player) {
        if (swordStack.getCount() != 1) return false;
        ItemStack other = slot.getItem();

        if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
            ItemStack old = tryInsertOrSwap(swordStack, other);
            if (old != null) {
                // old.isEmpty() → 空槽直接装入；!old.isEmpty() → 槽位冲突，替换出旧装备
                if (!old.isEmpty()) slot.set(old);
                playInsert(player, other);
            } else {
                playInsertFail(player); // 不是装备
            }
            broadcast(player);
            return true;
        }
        if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
            ItemStack removed = removeOne(swordStack);
            if (!removed.isEmpty()) {
                ItemStack leftover = slot.safeInsert(removed);
                if (!leftover.isEmpty()) forceInsert(swordStack, leftover);
                else playRemoveOne(player);
                broadcast(player);
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品栏交互：物品 → 剑（模仿 BundleItem.overrideOtherStackedOnMe）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack swordStack, ItemStack otherStack,
                                            Slot slot, ClickAction clickAction,
                                            Player player, SlotAccess slotAccess) {
        if (swordStack.getCount() != 1) return false;

        if (clickAction == ClickAction.PRIMARY && !otherStack.isEmpty()) {
            if (slot.allowModification(player)) {
                ItemStack old = tryInsertOrSwap(swordStack, otherStack);
                if (old != null) {
                    // 如果替换出了旧装备，还给光标
                    if (!old.isEmpty()) slotAccess.set(old);
                    playInsert(player, otherStack);
                } else {
                    playInsertFail(player);
                }
                broadcast(player);
            }
            return true;
        }
        if (clickAction == ClickAction.SECONDARY && otherStack.isEmpty()) {
            if (slot.allowModification(player)) {
                ItemStack removed = removeOne(swordStack);
                if (!removed.isEmpty()) {
                    playRemoveOne(player);
                    slotAccess.set(removed);
                }
                broadcast(player);
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // 物品栏进度条（4格满 = 13像素）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return countStored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int stored = countStored(stack);
        if (stored == 0) return 0;
        return Math.max(1, stored * 13 / SLOT_COUNT);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return countStored(stack) >= SLOT_COUNT ? 0xFF55FF55 : 0xFF70B0FF;
    }

    // ═══════════════════════════════════════════════════════════════
    // 提示框 — 贴图网格（待 mixin 修复杂志进度条）
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 TooltipDisplay display, Consumer<Component> adder,
                                 TooltipFlag flag) {
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        ItemContainerContents icc = stack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.EMPTY);

        List<ItemStack> nonEmpty = icc.stream().filter(s -> !s.isEmpty()).toList();
        if (nonEmpty.isEmpty()) return Optional.empty();

        BundleContents contents = new BundleContents(nonEmpty);
        // 将选中槽位映射为 BundleContents 中的索引，使提示框网格高亮选中物品
        int bundleIndex = getSelectedBundleIndex(stack);
        if (bundleIndex >= 0 && bundleIndex < nonEmpty.size()) {
            BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
            mutable.toggleSelectedItem(bundleIndex);
            contents = mutable.toImmutable();
        }
        return Optional.of(new BundleTooltip(contents));
    }

    // ═══════════════════════════════════════════════════════════════
    // 掉落保护
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        NonNullList<ItemStack> contents = readItems(itemEntity.getItem());
        itemEntity.getItem().set(ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.EMPTY);
        for (ItemStack s : contents)
            if (!s.isEmpty()) itemEntity.spawnAtLocation((ServerLevel) itemEntity.level(), s, 0.1F);
    }

    // ═══════════════════════════════════════════════════════════════
    // 数据读写
    // ═══════════════════════════════════════════════════════════════

    private static NonNullList<ItemStack> readItems(ItemStack stack) {
        ItemContainerContents icc = stack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(), ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        List<ItemStack> stored = icc.stream().toList();
        for (int i = 0; i < SLOT_COUNT && i < stored.size(); i++) {
            ItemStack s = stored.get(i);
            if (s != null && !s.isEmpty()) items.set(i, s.copy());
        }
        return items;
    }

    private static void writeItems(ItemStack stack, NonNullList<ItemStack> items) {
        List<ItemStack> list = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) list.add(items.get(i).copy());
        stack.set(ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.fromItems(list));
    }

    private int countStored(ItemStack stack) {
        int c = 0;
        for (ItemStack s : readItems(stack)) if (!s.isEmpty()) c++;
        return c;
    }

    // ═══════════════════════════════════════════════════════════════
    // 滚轮选取：选中槽位管理
    // ═══════════════════════════════════════════════════════════════

    /** 找到第一个非空槽位的索引，没有则返回 -1 */
    private static int findFirstNonEmpty(ItemStack stack) {
        NonNullList<ItemStack> items = readItems(stack);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items.get(i).isEmpty()) return i;
        }
        return -1;
    }

    /**
     * 获取当前选中的槽位索引。
     * 返回 -1 表示没有主动选中（此时取出按顺序，不干预）。
     */
    private static int getSelectedSlot(ItemStack stack) {
        Integer slot = stack.get(ModDataComponents.SELECTED_SLOT.get());
        if (slot == null || slot < 0 || slot >= SLOT_COUNT) {
            return -1;
        }
        NonNullList<ItemStack> items = readItems(stack);
        if (items.get(slot).isEmpty()) {
            return -1;
        }
        return slot;
    }

    /** 设置选中的槽位索引 */
    private static void setSelectedSlot(ItemStack stack, int slot) {
        if (slot >= -1 && slot < SLOT_COUNT) {
            stack.set(ModDataComponents.SELECTED_SLOT.get(), slot);
        }
    }

    /**
     * 服务端收到滚轮选择同步包后调用（公开）。
     * 仅校验是否为 ArmorStandSwordItem 后写入选中槽位。
     */
    public static void applySelectedSlot(ItemStack stack, int selectedSlot) {
        setSelectedSlot(stack, selectedSlot);
    }

    /**
     * 滚轮切换选中槽位（公开，供客户端事件调用）。
     * @param direction 正数 = 向下滚动 (下一个)，负数 = 向上滚动 (上一个)
     */
    public static void cycleSelectedSlot(ItemStack stack, int direction) {
        NonNullList<ItemStack> items = readItems(stack);
        int total = 0;
        for (ItemStack s : items) if (!s.isEmpty()) total++;
        if (total == 0) return;

        int current = getSelectedSlot(stack);
        int dir = direction > 0 ? 1 : -1;

        // 没有选中时：向下滚动从第一个开始，向上滚动从最后一个开始
        if (current < 0) {
            current = dir > 0 ? -1 : SLOT_COUNT;
        }

        // 循环跳过空槽
        for (int i = 0; i < SLOT_COUNT; i++) {
            current = (current + dir + SLOT_COUNT) % SLOT_COUNT;
            if (!items.get(current).isEmpty()) {
                setSelectedSlot(stack, current);
                return;
            }
        }
    }

    /**
     * 将槽位索引映射为 BundleContents 中的索引（仅非空物品参与排列）。
     * 用于提示框高亮当前选中的装备。
     */
    private static int getSelectedBundleIndex(ItemStack stack) {
        int selectedSlot = getSelectedSlot(stack);
        if (selectedSlot < 0) return -1;

        NonNullList<ItemStack> items = readItems(stack);
        int bundleIndex = 0;
        for (int i = 0; i < selectedSlot; i++) {
            if (!items.get(i).isEmpty()) bundleIndex++;
        }
        return bundleIndex;
    }

    // ═══════════════════════════════════════════════════════════════
    // 插入 / 取出
    // ═══════════════════════════════════════════════════════════════

    private int getSlotIndex(ItemStack stack) {
        var eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return -1;
        return switch (eq.slot()) {
            case HEAD -> 0; case CHEST, BODY -> 1; case LEGS -> 2; case FEET -> 3;
            default -> -1;
        };
    }

    /**
     * 尝试装入或替换。
     * @return 被替换出的旧装备（空表示空槽直装），null 表示物品不是装备
     */
    private ItemStack tryInsertOrSwap(ItemStack swordStack, ItemStack toInsert) {
        int idx = getSlotIndex(toInsert);
        if (idx < 0) return null; // 不是装备

        NonNullList<ItemStack> items = readItems(swordStack);
        ItemStack old = items.get(idx).copy();
        items.set(idx, toInsert.split(1));
        writeItems(swordStack, items);
        return old; // empty=空槽装入, !empty=替换
    }

    private void forceInsert(ItemStack swordStack, ItemStack toInsert) {
        int idx = getSlotIndex(toInsert);
        if (idx < 0) return;
        NonNullList<ItemStack> items = readItems(swordStack);
        items.set(idx, toInsert);
        writeItems(swordStack, items);
    }

    private ItemStack removeOne(ItemStack swordStack) {
        NonNullList<ItemStack> items = readItems(swordStack);
        int selected = getSelectedSlot(swordStack);

        // 有主动选中 → 取出选中的装备，然后清除选中状态
        if (selected >= 0 && !items.get(selected).isEmpty()) {
            ItemStack removed = items.get(selected);
            items.set(selected, ItemStack.EMPTY);
            writeItems(swordStack, items);
            setSelectedSlot(swordStack, -1); // 清除选中，回到"无干预"模式
            return removed;
        }

        // 没有选中 → 按顺序取出第一个（原始行为）
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items.get(i).isEmpty()) {
                ItemStack removed = items.get(i);
                items.set(i, ItemStack.EMPTY);
                writeItems(swordStack, items);
                return removed;
            }
        }
        return ItemStack.EMPTY;
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════

    private void broadcast(Player p) {
        AbstractContainerMenu m = p.containerMenu;
        if (m != null) m.slotsChanged(p.getInventory());
    }

    private static void playRemoveOne(Entity e) {
        e.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + e.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playInsert(Entity e, ItemStack armor) {
        var eq = armor.get(DataComponents.EQUIPPABLE);
        if (eq != null) {
            e.playSound(eq.equipSound().value(), 1.0F, 1.0F);
        } else {
            e.playSound(SoundEvents.BUNDLE_INSERT, 1.0F, 1.0F);
        }
    }

    private static void playInsertFail(Entity e) {
        e.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }
}

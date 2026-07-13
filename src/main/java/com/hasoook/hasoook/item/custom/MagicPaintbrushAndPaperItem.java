package com.hasoook.hasoook.item.custom;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MagicPaintbrushAndPaperItem extends Item {

    private static final int EQUIP_INTERVAL = 200; // 10 秒 = 200 ticks
    private static final float EQUIP_CHANCE = 0.5F; // 50% 概率
    private static final String TAG_TIMER = "EquipmentTimer";

    // 懒加载缓存：从注册表自动收集
    private static List<Item> toolsCache = null;
    private static List<Item> armorsCache = null;

    public MagicPaintbrushAndPaperItem(Properties properties) {
        super(properties.rarity(Rarity.UNCOMMON).stacksTo(1));
    }

    /**
     * 从注册表自动收集所有工具/武器与盔甲类物品
     */
    private static void ensurePoolsLoaded(ServerLevel level) {
        if (toolsCache != null) return;

        var items = level.registryAccess().lookupOrThrow(Registries.ITEM);
        Set<Item> tools = new LinkedHashSet<>();
        Set<Item> armors = new LinkedHashSet<>();

        // --- 工具：通过原版标签收集 ---
        addTagItems(items, tools, ItemTags.SWORDS);
        addTagItems(items, tools, ItemTags.PICKAXES);
        addTagItems(items, tools, ItemTags.AXES);
        addTagItems(items, tools, ItemTags.SHOVELS);
        addTagItems(items, tools, ItemTags.HOES);

        // 不属于上面标签的武器类型，用 class 判断
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem
                || item instanceof MaceItem) {
                tools.add(item);
            }
        }

        // --- 盔甲：通过原版标签收集，仅保留有合法盔甲槽位的物品 ---
        addArmorTagItems(items, armors, ItemTags.HEAD_ARMOR);
        addArmorTagItems(items, armors, ItemTags.CHEST_ARMOR);
        addArmorTagItems(items, armors, ItemTags.LEG_ARMOR);
        addArmorTagItems(items, armors, ItemTags.FOOT_ARMOR);
        // 移除盾牌和其他非盔甲物品
        armors.remove(Items.SHIELD);

        toolsCache = new ArrayList<>(tools);
        armorsCache = new ArrayList<>(armors);
    }

    /**
     * 将某个物品标签下的所有物品加入集合
     */
    private static void addTagItems(net.minecraft.core.HolderLookup.RegistryLookup<Item> lookup,
                                     Set<Item> set, TagKey<Item> tagKey) {
        lookup.get(tagKey).ifPresent(named -> {
            for (var holder : named) {
                set.add(holder.value());
            }
        });
    }

    /**
     * 将某个盔甲标签下的物品加入集合（仅保留装备槽位合法的物品）
     */
    private static void addArmorTagItems(net.minecraft.core.HolderLookup.RegistryLookup<Item> lookup,
                                          Set<Item> set, TagKey<Item> tagKey) {
        lookup.get(tagKey).ifPresent(named -> {
            for (var holder : named) {
                Item item = holder.value();
                EquipmentSlot slot = new ItemStack(item).getEquipmentSlot();
                // 必须有合法的盔甲槽位（HEAD/CHEST/LEGS/FEET）
                if (slot == EquipmentSlot.HEAD
                    || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS
                    || slot == EquipmentSlot.FEET) {
                    set.add(item);
                }
            }
        });
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);

        // 只有敌对生物（僵尸、骷髅、凋零骷髅等）生效
        if (!(entity instanceof Mob mob)) return;
        if (!(mob instanceof Monster)) return;

        // 只有拿在主手或副手时才触发
        boolean heldInMainHand = mob.getMainHandItem() == stack;
        boolean heldInOffHand = mob.getOffhandItem() == stack;
        if (!heldInMainHand && !heldInOffHand) return;

        // 首次调用时从注册表加载物品池
        ensurePoolsLoaded(level);

        // 计时器
        CompoundTag tag = getOrCreateTag(stack);
        int timer = tag.getInt(TAG_TIMER).orElse(0);
        timer++;

        if (timer >= EQUIP_INTERVAL) {
            timer = 0;
            // 50% 概率触发
            if (level.random.nextFloat() < EQUIP_CHANCE) {
                equipRandomItem(mob, level.random, stack);
            }
        }

        tag.putInt(TAG_TIMER, timer);
        saveTag(stack, tag);
    }

    /**
     * 随机给怪物装备一件工具或盔甲，槽位冲突时直接覆盖
     */
    private void equipRandomItem(Mob mob, RandomSource random, ItemStack paintbrush) {
        if (random.nextBoolean()) {
            // --- 工具：装备到主手，画笔自动移到副手 ---
            if (toolsCache.isEmpty()) return;
            Item tool = toolsCache.get(random.nextInt(toolsCache.size()));
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            // 画笔移到副手
            mob.setItemSlot(EquipmentSlot.OFFHAND, paintbrush);
        } else {
            // --- 盔甲：装备到对应槽位 ---
            if (armorsCache.isEmpty()) return;
            Item armorItem = armorsCache.get(random.nextInt(armorsCache.size()));
            ItemStack armorStack = new ItemStack(armorItem);
            EquipmentSlot equipSlot = armorStack.getEquipmentSlot();
            // 安全检查：确保槽位不为空且是盔甲槽
            if (equipSlot != null && equipSlot.isArmor()) {
                mob.setItemSlot(equipSlot, armorStack);
                mob.setDropChance(equipSlot, 0.0F);
            }
        }
    }

    /**
     * 获取物品的 NBT 数据
     */
    private static CompoundTag getOrCreateTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /**
     * 将 NBT 数据写回物品
     */
    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
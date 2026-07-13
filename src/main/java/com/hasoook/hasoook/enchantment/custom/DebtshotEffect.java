package com.hasoook.hasoook.enchantment.custom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

public record DebtshotEffect(LevelBasedValue probability) implements EnchantmentEntityEffect {

    public static final MapCodec<DebtshotEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LevelBasedValue.CODEC.fieldOf("probability").forGetter(DebtshotEffect::probability)
            ).apply(instance, DebtshotEffect::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        if (item.owner() instanceof ServerPlayer player) {

            if (level.getRandom().nextFloat() < this.probability.calculate(enchantmentLevel)) {

                // 识别箭类型（兼容所有箭矢）
                Item arrowItem = Items.ARROW;
                if (entity instanceof AbstractArrow arrow) {
                    arrowItem = arrow.getPickupItemStackOrigin().getItem();
                }
                entity.discard();

                CompoundTag persistentData = player.getPersistentData();

                // 读取债务（不存在时默认为0）
                int oldDebt = persistentData.getIntOr("debtshot_debt", 0);
                int debtPerShot = enchantmentLevel + 1;          // 本次欠债 = 1 + 附魔等级
                int totalIncurred = oldDebt + debtPerShot;       // 此次操作总共造成的债务（偿还前）

                int debt = oldDebt;

                // 先还旧债
                int canPayNow = Math.min(debt, countArrows(player, arrowItem));
                if (canPayNow > 0) {
                    removeArrows(player, arrowItem, canPayNow);
                    debt -= canPayNow;
                }

                // 增加新债
                debt += debtPerShot;

                // 再尝试偿还
                canPayNow = Math.min(debt, countArrows(player, arrowItem));
                if (canPayNow > 0) {
                    removeArrows(player, arrowItem, canPayNow);
                    debt -= canPayNow;
                }

                // 保存债务
                persistentData.putInt("debtshot_debt", debt);

                // 使用翻译键发送消息
                if (debt > 0) {
                    player.sendSystemMessage(
                            Component.translatable("message.hasoook.debtshot.debt", debt)
                    );
                } else {
                    player.sendSystemMessage(
                            Component.translatable("message.hasoook.debtshot.cleared", totalIncurred)
                    );
                }
            }
        }
    }

    private int countArrows(ServerPlayer player, Item arrowItem) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(arrowItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeArrows(ServerPlayer player, Item arrowItem, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
            if (stack.is(arrowItem)) {
                int toTake = Math.min(stack.getCount(), remaining);
                stack.shrink(toTake);
                remaining -= toTake;
            }
        }
    }

    @Override
    public MapCodec<? extends EnchantmentEntityEffect> codec() {
        return CODEC;
    }
}
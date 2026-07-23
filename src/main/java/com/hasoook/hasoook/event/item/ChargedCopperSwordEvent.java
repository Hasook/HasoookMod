package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.ChargedCopperAxeItem;
import com.hasoook.hasoook.item.custom.ChargedCopperHoeItem;
import com.hasoook.hasoook.item.custom.ChargedCopperPickaxeItem;
import com.hasoook.hasoook.item.custom.ChargedCopperShovelItem;
import com.hasoook.hasoook.item.custom.ChargedCopperSwordItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 蓄电铜剑雷击充能事件 —— 当避雷针被雷劈中，
 * 且下方方块是铜箱子时，为箱内所有蓄电铜剑充能。
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class ChargedCopperSwordEvent {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只处理闪电实体
        if (!(event.getEntity() instanceof LightningBolt lightning)) {
            return;
        }

        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        // 获取闪电位置
        BlockPos lightningPos = lightning.blockPosition();

        // 检查该位置（或其上方相邻位置）是否为避雷针
        BlockPos rodPos = findLightningRod(level, lightningPos);
        if (rodPos == null) {
            return;
        }

        // 检查避雷针下方方块是否为铜箱子
        BlockPos belowRodPos = rodPos.below();
        BlockState belowState = level.getBlockState(belowRodPos);
        if (!(belowState.getBlock() instanceof CopperChestBlock)) {
            return;
        }

        // 获取铜箱子方块实体，遍历物品栏充能
        BlockEntity blockEntity = level.getBlockEntity(belowRodPos);
        if (blockEntity instanceof ChestBlockEntity chest) {
            int chargedCount = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (ChargedCopperSwordItem.isChargedCopperSword(stack)
                        || ChargedCopperPickaxeItem.isChargedCopperPickaxe(stack)
                        || ChargedCopperAxeItem.isChargedCopperAxe(stack)
                        || ChargedCopperHoeItem.isChargedCopperHoe(stack)
                        || ChargedCopperShovelItem.isChargedCopperShovel(stack)) {
                    ChargedCopperSwordItem.addCharge(stack, ChargedCopperSwordItem.CHARGE_PER_STRIKE);
                    chest.setItem(slot, stack);
                    chargedCount++;
                }
            }

            // 储电瓶 → 瓶中闪电转换：每次雷击最多转换16瓶
            int convertedCount = 0;
            int maxConvert = 16;

            for (int slot = 0; slot < chest.getContainerSize() && convertedCount < maxConvert; slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.is(ModItems.CHARGE_BOTTLE.get())) {
                    int toConvert = Math.min(stack.getCount(), maxConvert - convertedCount);
                    stack.shrink(toConvert);
                    chest.setItem(slot, stack);
                    convertedCount += toConvert;

                    // 创建瓶中闪电
                    ItemStack lightningStack = new ItemStack(ModItems.BOTTLED_LIGHTNING.get(), toConvert);

                    // 先尝试合并到已有的瓶中闪电堆中
                    for (int s = 0; s < chest.getContainerSize() && !lightningStack.isEmpty(); s++) {
                        ItemStack existing = chest.getItem(s);
                        if (!existing.isEmpty() && existing.is(ModItems.BOTTLED_LIGHTNING.get()) && existing.getCount() < existing.getMaxStackSize()) {
                            int mergeAmount = Math.min(existing.getMaxStackSize() - existing.getCount(), lightningStack.getCount());
                            existing.grow(mergeAmount);
                            lightningStack.shrink(mergeAmount);
                            chest.setItem(s, existing);
                        }
                    }
                    // 将剩余的放入空槽位
                    for (int s = 0; s < chest.getContainerSize() && !lightningStack.isEmpty(); s++) {
                        if (chest.getItem(s).isEmpty()) {
                            chest.setItem(s, lightningStack.copy());
                            lightningStack.setCount(0);
                        }
                    }
                }
            }

            if (chargedCount > 0 || convertedCount > 0) {
                chest.setChanged(); // 标记方块实体数据已变更，触发保存
                // 可选：播放粒子效果或音效作为反馈
                level.levelEvent(3002, belowRodPos, 0); // 箱子开启音效
            }
        }
    }

    /**
     * 在给定位置附近寻找避雷针方块。
     * 闪电可能落在避雷针本体或避雷针顶部，因此检查 lightningPos 及其相邻位置。
     */
    private static BlockPos findLightningRod(Level level, BlockPos lightningPos) {
        // 直接命中位置
        if (isLightningRod(level, lightningPos)) {
            return lightningPos;
        }
        // 闪电可能落在避雷针上方一格（避雷针附着的方块顶部）
        BlockPos above = lightningPos.above();
        if (isLightningRod(level, above)) {
            return above;
        }
        // 闪电可能落在避雷针下方的方块上
        BlockPos below = lightningPos.below();
        if (isLightningRod(level, below)) {
            return below;
        }
        return null;
    }

    private static boolean isLightningRod(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.LIGHTNING_ROD);
    }
}

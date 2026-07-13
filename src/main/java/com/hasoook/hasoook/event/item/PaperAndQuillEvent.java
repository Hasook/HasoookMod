package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class PaperAndQuillEvent {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();

        if (!player.level().isClientSide() && Config.PAQ_LIMIT_DROPS.get()) {
            BlockState state = event.getState();
            Item dyeToDrop = null;

            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = event.getPos();

            if (state.is(BlockTags.LEAVES)) {
                // 概率掉落
                if (level.random.nextFloat() < 0.2f) {

                    Item[] dyes = new Item[] {
                            Items.WHITE_DYE,
                            Items.BLACK_DYE,
                            Items.RED_DYE,
                            Items.BLUE_DYE,
                            Items.GREEN_DYE,
                            Items.YELLOW_DYE,
                            Items.PURPLE_DYE,
                            Items.CYAN_DYE,
                            Items.LIGHT_BLUE_DYE,
                            Items.LIME_DYE,
                            Items.PINK_DYE,
                            Items.GRAY_DYE,
                            Items.LIGHT_GRAY_DYE,
                            Items.BROWN_DYE,
                            Items.ORANGE_DYE,
                            Items.MAGENTA_DYE
                    };

                    Item randomDye = dyes[level.random.nextInt(dyes.length)];

                    ItemStack dropStack = new ItemStack(randomDye, 1);
                    ItemEntity itemEntity = new ItemEntity(
                            level,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            dropStack
                    );

                    itemEntity.setPickUpDelay(10);
                    level.addFreshEntity(itemEntity);
                }

                return;
            }

            if (state.is(BlockTags.LOGS)) {
                dyeToDrop = Items.BROWN_DYE;
            } else if (state.is(Blocks.STONE)) {
                dyeToDrop = Items.LIGHT_GRAY_DYE;
            } else if (state.is(BlockTags.COAL_ORES)) {
                dyeToDrop = Items.GRAY_DYE;
            } else if (state.is(BlockTags.DIAMOND_ORES)) {
                dyeToDrop = Items.LIGHT_BLUE_DYE;
            } else if (state.is(BlockTags.IRON_ORES)) {
                dyeToDrop = Items.WHITE_DYE;
            } else if (state.is(BlockTags.GOLD_ORES)) {
                dyeToDrop = Items.YELLOW_DYE;
            }

            if (dyeToDrop != null) {
                ItemStack dropStack = new ItemStack(dyeToDrop, 1);
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        dropStack
                );

                itemEntity.setPickUpDelay(10);
                level.addFreshEntity(itemEntity);

                level.removeBlock(pos, false);
            }
        }
    }

    // 玩家加入服务器时触发
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        giveStarterItems(event.getEntity());
    }

    // 玩家重生时触发
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        giveStarterItems(event.getEntity());
    }

    // 统一的发放逻辑
    private static void giveStarterItems(Player player) {
        // 只在服务端执行
        if (player.level().isClientSide()) return;
        // 检查配置是否启用
        if (!Config.PAQ_GIVE_ON_JOIN.get()) return;

        ItemStack magicStack = new ItemStack(ModItems.MAGIC_PAINTBRUSH_AND_PAPER.get());
        // 如果玩家物品栏中已经拥有神奇的笔与纸，则不再给予整套物品
        if (player.getInventory().contains(magicStack)) return;

        // 给予神奇的笔与纸
        player.addItem(magicStack.copy());
        // 给予书与笔
        player.addItem(new ItemStack(Items.WRITABLE_BOOK));
    }
}
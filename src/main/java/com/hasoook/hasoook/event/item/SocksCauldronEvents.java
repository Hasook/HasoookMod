package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.SocksItem;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SocksCauldronEvents {
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof SocksItem)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.WATER_CAULDRON)) {
                return ActionResult.PASS;
            }

            int currentWear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
            int currentStage = SocksItem.getStage(currentWear);

            if (currentStage == 0) {
                return ActionResult.PASS; // 已经干净了
            }

            // 降低 2 级，最低为 0
            int targetStage = Math.max(0, currentStage - 2);
            stack.set(ModDataComponents.SOCKS_WEAR, SocksItem.getMinWearForStage(targetStage));

            // ── 减少炼药锅水位 ──
            int waterLevel = world.getBlockState(pos).get(LeveledCauldronBlock.LEVEL);
            ServerWorld serverWorld = (ServerWorld) world;

            if (waterLevel > 1) {
                world.setBlockState(pos, world.getBlockState(pos).with(LeveledCauldronBlock.LEVEL, waterLevel - 1), 3);
            } else {
                world.setBlockState(pos, Blocks.CAULDRON.getDefaultState(), 3);
            }

            // ── 音效 ──
            serverWorld.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.6f, 1.2f);

            // ── 粒子 ──
            serverWorld.spawnParticles(ParticleTypes.SPLASH,
                    pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    8, 0.25, 0.1, 0.25, 0.05);

            // 如果玩家在主手拿袜子尝试右键装备，炼药锅仍然接受
            if (hand == player.getActiveHand() || true) {
                return ActionResult.SUCCESS;
            }

            return ActionResult.SUCCESS;
        });
    }
}

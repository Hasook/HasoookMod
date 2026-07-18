package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.block.custom.BuildingBlockCarpetBlock;
import com.hasoook.hasoook.event.block.BuildingBlockStepEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 积木物品 — 右键地毯/床时堆叠，最大 16 个。
 */
public class BuildingBlockItem extends BlockItem {

    private static final int MAX_BLOCKS = 16;

    public BuildingBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState target = level.getBlockState(context.getClickedPos());
        Block targetBlock = target.getBlock();

        // ── 床：塞入积木 ──
        if (targetBlock instanceof BedBlock) {
            if (!level.isClientSide()) {
                int current = BuildingBlockStepEvent.getBedBlockCount(context.getClickedPos());
                if (current >= MAX_BLOCKS) return InteractionResult.FAIL;
                BuildingBlockStepEvent.setBedBlockCount(context.getClickedPos(), current + 1);

                // 使用和地毯塞入一致的羊毛音效
                level.playSound(null, context.getClickedPos(), SoundType.WOOL.getPlaceSound(), SoundSource.BLOCKS,
                        (SoundType.WOOL.getVolume() + 1.0F) / 2.0F, SoundType.WOOL.getPitch() * 0.8F);

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.DUST_PLUME,
                            context.getClickedPos().getX() + 0.5,
                            context.getClickedPos().getY() + 0.5,
                            context.getClickedPos().getZ() + 0.5,
                            4, 0.15, 0.05, 0.15, 0.02);
                }

                if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // ── 已陷阱化的地毯：叠加积木 ──
        if (targetBlock instanceof BuildingBlockCarpetBlock) {
            int current = target.getValue(BuildingBlockCarpetBlock.BLOCKS);
            if (current >= MAX_BLOCKS) return InteractionResult.FAIL;

            if (!level.isClientSide()) {
                BlockState newState = target.setValue(BuildingBlockCarpetBlock.BLOCKS, current + 1);
                level.setBlock(context.getClickedPos(), newState, Block.UPDATE_ALL);

                SoundType sound = newState.getSoundType(level, context.getClickedPos(), context.getPlayer());
                level.playSound(null, context.getClickedPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

                // 灰尘粒子
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.DUST_PLUME,
                            context.getClickedPos().getX() + 0.5,
                            context.getClickedPos().getY() + 0.3,
                            context.getClickedPos().getZ() + 0.5,
                            4, 0.15, 0.05, 0.15, 0.02);
                }

                if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // ── 普通地毯：创建陷阱地毯（1 个积木）──
        if (targetBlock instanceof CarpetBlock) {
            if (!level.isClientSide()) {
                DyeColor color = getCarpetColor(targetBlock);
                BlockState trapped = ModBlocks.BUILDING_BLOCK_CARPET.get().defaultBlockState()
                        .setValue(BuildingBlockCarpetBlock.COLOR, color)
                        .setValue(BuildingBlockCarpetBlock.BLOCKS, 1);

                level.setBlock(context.getClickedPos(), trapped, Block.UPDATE_ALL);

                SoundType sound = trapped.getSoundType(level, context.getClickedPos(), context.getPlayer());
                level.playSound(null, context.getClickedPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);

                // 灰尘粒子
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.DUST_PLUME,
                            context.getClickedPos().getX() + 0.5,
                            context.getClickedPos().getY() + 0.3,
                            context.getClickedPos().getZ() + 0.5,
                            4, 0.15, 0.05, 0.15, 0.02);
                }

                if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 非地毯走原版放置
        return super.useOn(context);
    }

    private static DyeColor getCarpetColor(Block block) {
        for (DyeColor color : DyeColor.values()) {
            if (BuildingBlockCarpetBlock.getVanillaCarpet(color) == block) return color;
        }
        return DyeColor.WHITE;
    }
}

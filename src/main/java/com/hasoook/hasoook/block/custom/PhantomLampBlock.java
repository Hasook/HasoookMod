package com.hasoook.hasoook.block.custom;

import com.hasoook.hasoook.block.entity.ModBlockEntities;
import com.hasoook.hasoook.block.entity.custom.PhantomLampBlockEntity;
import com.hasoook.hasoook.item.custom.PhantomLampBlockItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class PhantomLampBlock extends BaseEntityBlock {
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape SHAPE_STANDING = Shapes.or(
            Block.box(5.0, 0.0, 5.0, 11.0, 10.0, 11.0),
            Block.box(6.0, 10.0, 6.0, 10.0, 12.0, 10.0)
    );
    private static final VoxelShape SHAPE_HANGING = Shapes.or(
            Block.box(5.0, 2.0, 5.0, 11.0, 12.0, 11.0),
            Block.box(6.0, 12.0, 6.0, 10.0, 14.0, 10.0),
            Block.box(6.5, 14.0, 6.5, 9.5, 16.0, 9.5)
    );

    public PhantomLampBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HANGING, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(PhantomLampBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhantomLampBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.PHANTOM_LAMP.get(), PhantomLampBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? SHAPE_HANGING : SHAPE_STANDING;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        Direction direction = context.getClickedFace();
        boolean hanging = direction == Direction.DOWN;
        return this.defaultBlockState()
                .setValue(HANGING, hanging)
                .setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HANGING)) {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            return aboveState.isFaceSturdy(level, above, Direction.DOWN);
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PhantomLampBlockEntity lampEntity)) {
            return InteractionResult.PASS;
        }

        // Broken lamps do nothing
        if (lampEntity.isBroken()) {
            player.displayClientMessage(
                    Component.translatable("message.hasoook.phantom_lamp.broken"),
                    true);
            return InteractionResult.SUCCESS;
        }

        // Shift + right-click: start freeze charge (only for pristine lamps)
        if (player.isShiftKeyDown()) {
            if (lampEntity.canFreeze() && !lampEntity.isFreezing() && !lampEntity.isCharging()) {
                lampEntity.startCharging(player.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.hasoook.phantom_lamp.charge_start"),
                        true);
            } else if (!lampEntity.canFreeze()) {
                player.displayClientMessage(
                        Component.translatable("message.hasoook.phantom_lamp.repaired_no_freeze"),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.hasoook.phantom_lamp.busy"),
                        true);
            }
            return InteractionResult.SUCCESS;
        }

        // Busy check: prevent use while charging, freezing, or already grabbing
        if (lampEntity.isCharging() || lampEntity.isFreezing() || lampEntity.isPhantomGrabbing()) {
            player.displayClientMessage(
                    Component.translatable("message.hasoook.phantom_lamp.busy"),
                    true);
            return InteractionResult.SUCCESS;
        }

        // Normal right-click: summon Phantoms at the lamp; each flies to a hostile mob, grabs it, ascends, then vanishes
        AABB area = new AABB(pos).inflate(16);
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, area, m -> true);

        if (!monsters.isEmpty()) {
            Map<UUID, UUID> phantomToTarget = new HashMap<>();

            for (Monster monster : monsters) {
                // Spawn a Phantom at the lamp position
                Phantom phantom = new Phantom(EntityType.PHANTOM, level);
                phantom.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
                phantom.setNoAi(true);
                phantom.setInvulnerable(true);
                phantom.setSilent(true);
                level.addFreshEntity(phantom);

                phantomToTarget.put(phantom.getUUID(), monster.getUUID());

                // Mark the target with particles
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            monster.getX(), monster.getY() + monster.getBbHeight() / 2, monster.getZ(),
                            5, 0.3, 0.3, 0.3, 0.05);
                }
            }

            // Flash particles at the lamp on activation
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        25, 0.5, 0.5, 0.5, 0.12);
            }

            // Start the grab sequence — phantoms will approach, grab, ascend, then disappear
            lampEntity.startPhantomGrab(phantomToTarget);

            player.displayClientMessage(
                    Component.translatable("message.hasoook.phantom_lamp.grab", monsters.size()),
                    true);
        } else {
            player.displayClientMessage(
                    Component.translatable("message.hasoook.phantom_lamp.grab_none"),
                    true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Transfer repaired state from item to block entity
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PhantomLampBlockEntity lampEntity) {
                lampEntity.setLampState(PhantomLampBlockItem.getState(stack));
            }
        }
    }
}

package com.hasoook.hasoook.block.custom;

import com.hasoook.hasoook.block.entity.custom.MobHeadBlockEntity;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * 生物头方块。
 * <p>
 * 可以被放置在方块顶面上，支持 16 个旋转方向。
 * 内部通过 {@link MobHeadBlockEntity} 存储生物类型 / 玩家皮肤信息，
 * 渲染由 {@link com.hasoook.hasoook.client.renderer.MobHeadBlockRenderer}（BER）处理。
 * <p>
 * 掉落由 loot table 处理，通过 {@link MobHeadBlockEntity#collectImplicitComponents}
 * 将数据复制到掉落物品上。
 */
public class MobHeadBlock extends BaseEntityBlock {

    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE_UP = Block.box(4.0, 0.0, 4.0, 12.0, 8.0, 12.0);
    private static final VoxelShape SHAPE_NORTH = Block.box(4.0, 4.0, 8.0, 12.0, 12.0, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 8.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 4.0, 4.0, 8.0, 12.0, 12.0);
    private static final VoxelShape SHAPE_WEST = Block.box(8.0, 4.0, 4.0, 16.0, 12.0, 12.0);

    public MobHeadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(ROTATION, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MobHeadBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_UP;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobHeadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旋转角度：0-15，根据玩家朝向
        int rotation = (int) Math.floor((double) (context.getRotation() * 16.0F / 360.0F) + 0.5D) & 15;
        Direction clickedFace = context.getClickedFace();

        if (clickedFace == Direction.UP) {
            // 放在方块顶部 → 地面放置
            return this.defaultBlockState().setValue(FACING, Direction.UP).setValue(ROTATION, rotation);
        } else if (clickedFace != Direction.DOWN) {
            // 放在方块侧面 → 墙面附着
            return this.defaultBlockState().setValue(FACING, clickedFace).setValue(ROTATION, rotation);
        }
        // 点击底面 → 放在该方块顶部
        return this.defaultBlockState().setValue(FACING, Direction.UP).setValue(ROTATION, rotation);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MobHeadBlockEntity headBe) {
                String entityType = stack.get(ModDataComponents.MOB_HEAD_TYPE.get());
                String ownerName = stack.get(ModDataComponents.HEAD_OWNER_NAME.get());
                String ownerUuid = stack.get(ModDataComponents.HEAD_OWNER_UUID.get());
                headBe.setAll(
                        entityType != null ? entityType : "",
                        ownerName != null ? ownerName : "",
                        ownerUuid != null ? ownerUuid : ""
                );
            }
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.UP) {
            // 地面放置：使用 CENTER 支持类型以支持铁砧等非完整方块
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP, SupportType.CENTER);
        } else {
            // 墙面附着：检查附着面是否牢固
            BlockPos attachPos = pos.relative(facing.getOpposite());
            return level.getBlockState(attachPos).isFaceSturdy(level, attachPos, facing);
        }
    }

    /**
     * 中键选取时返回带有正确数据组件的物品。
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = new ItemStack(ModItems.MOB_HEAD.get());
        if (includeData) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MobHeadBlockEntity headBe) {
                String et = headBe.getEntityType();
                String pn = headBe.getPlayerName();
                String pu = headBe.getPlayerUuid();
                if (et != null && !et.isEmpty()) {
                    stack.set(ModDataComponents.MOB_HEAD_TYPE.get(), et);
                }
                if (pn != null && !pn.isEmpty()) {
                    stack.set(ModDataComponents.HEAD_OWNER_NAME.get(), pn);
                }
                if (pu != null && !pu.isEmpty()) {
                    stack.set(ModDataComponents.HEAD_OWNER_UUID.get(), pu);
                }
            }
        }
        return stack;
    }
}

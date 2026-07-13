package com.hasoook.hasoook.block.entity.custom;

import com.hasoook.hasoook.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * 生物头方块的方块实体。
 * <p>
 * 存储头部类型（实体 ID）、玩家名（仅玩家头）和玩家 UUID（用于皮肤查询）。
 * 数据通过 {@link ClientboundBlockEntityDataPacket} 同步到客户端供 BER 渲染。
 */
public class MobHeadBlockEntity extends BlockEntity {

    private String entityType = "";
    private String playerName = "";
    private String playerUuid = "";

    public MobHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_HEAD.get(), pos, state);
    }

    // ==================== Getters / Setters ====================

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String type) {
        this.entityType = type != null ? type : "";
        setChangedAndSync();
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        this.playerName = name != null ? name : "";
        setChangedAndSync();
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String uuid) {
        this.playerUuid = uuid != null ? uuid : "";
        setChangedAndSync();
    }

    /**
     * 从 ItemStack 的数据组件中一次性设置所有头部数据。
     */
    public void setAll(String entityType, String playerName, String playerUuid) {
        this.entityType = entityType != null ? entityType : "";
        this.playerName = playerName != null ? playerName : "";
        this.playerUuid = playerUuid != null ? playerUuid : "";
        setChangedAndSync();
    }

    // ==================== Persistence (NeoForge ValueInput/ValueOutput API) ====================

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!entityType.isEmpty()) {
            output.putString("EntityType", entityType);
        }
        if (!playerName.isEmpty()) {
            output.putString("PlayerName", playerName);
        }
        if (!playerUuid.isEmpty()) {
            output.putString("PlayerUuid", playerUuid);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        entityType = input.getStringOr("EntityType", "");
        playerName = input.getStringOr("PlayerName", "");
        playerUuid = input.getStringOr("PlayerUuid", "");
    }

    // ==================== Client Sync ====================

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 通过 NeoForge 的 ValueOutput 序列化管线生成客户端同步数据。
     * <p>
     * 这等价于原版 {@code SkullBlockEntity.getUpdateTag} 调用
     * {@code this.saveCustomOnly(registries)} 的做法，
     * 确保序列化格式与客户端 {@link #loadAdditional(ValueInput)} 完全兼容。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    // ==================== Internal ====================

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // 强制同步方块实体数据到客户端（用于渲染头部模型）
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
        }
    }
}

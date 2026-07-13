package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：同步盔甲架剑的滚轮选中槽位。
 *
 * @param containerSlotId 容器格子索引（AbstractContainerMenu.slots 的索引）
 * @param selectedSlot    选中的装备槽位 (0-3)，-1 表示取消选中
 */
public record SelectSwordSlotPayload(int containerSlotId, int selectedSlot) implements CustomPacketPayload {

    public static final Type<SelectSwordSlotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "select_sword_slot"));

    public static final StreamCodec<FriendlyByteBuf, SelectSwordSlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.containerSlotId);
                        buf.writeVarInt(payload.selectedSlot);
                    },
                    buf -> new SelectSwordSlotPayload(buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

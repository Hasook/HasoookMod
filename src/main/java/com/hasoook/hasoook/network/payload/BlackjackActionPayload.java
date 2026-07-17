package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：玩家在黑杰克游戏中的操作
 *
 * @param action 操作类型: 0=选择倍率, 1=要牌, 2=停牌, 3=认输
 * @param data   附加数据（选择倍率时为倍率值，其他情况忽略）
 */
public record BlackjackActionPayload(int action, int data) implements CustomPacketPayload {

    public static final Type<BlackjackActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "blackjack_action"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.action);
                        buf.writeVarInt(payload.data);
                    },
                    buf -> new BlackjackActionPayload(buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

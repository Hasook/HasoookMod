package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：斗地主游戏操作
 *
 * @param action   操作类型: 0=选择倍率, 1=叫地主, 2=出牌, 3=不出
 * @param data     附加数据（倍率值、叫分等）
 * @param cardMask 出牌时选中的卡牌位掩码（bit i = card i）
 */
public record DouDiZhuActionPayload(int action, int data, long cardMask) implements CustomPacketPayload {

    public static final Type<DouDiZhuActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "doudizhu_action"));

    public static final StreamCodec<FriendlyByteBuf, DouDiZhuActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.action);
                        buf.writeVarInt(p.data);
                        buf.writeLong(p.cardMask);
                    },
                    buf -> new DouDiZhuActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readLong())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

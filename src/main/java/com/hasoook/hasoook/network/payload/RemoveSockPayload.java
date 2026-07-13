package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：移除指定索引的糊脸袜子。
 */
public record RemoveSockPayload(int index) implements CustomPacketPayload {

    public static final Type<RemoveSockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "remove_sock"));

    public static final StreamCodec<FriendlyByteBuf, RemoveSockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.index),
                    buf -> new RemoveSockPayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

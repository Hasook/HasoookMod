package com.hasoook.hasoook.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FpsPayload(int fps) implements CustomPacketPayload {

    public static final Type<FpsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("hasoook", "fps_sync"));

    public static final StreamCodec<FriendlyByteBuf, FpsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeInt(payload.fps),
                    buf -> new FpsPayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


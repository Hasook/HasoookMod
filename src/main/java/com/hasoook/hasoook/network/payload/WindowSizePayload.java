package com.hasoook.hasoook.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WindowSizePayload(int width, int height) implements CustomPacketPayload {

    public static final Type<WindowSizePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("hasoook", "window_size"));

    public static final StreamCodec<FriendlyByteBuf, WindowSizePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.width());
                        buf.writeInt(payload.height());
                    },
                    buf -> new WindowSizePayload(buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


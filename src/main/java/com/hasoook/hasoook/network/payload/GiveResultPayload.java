package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GiveResultPayload(String id, boolean isEntity) implements CustomPacketPayload {

    public static final Type<GiveResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "give_result"));

    public static final StreamCodec<FriendlyByteBuf, GiveResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.id);
                        buf.writeBoolean(payload.isEntity);
                    },
                    buf -> new GiveResultPayload(buf.readUtf(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DrawSaveMapPayload(int[] pixels, String itemName) implements CustomPacketPayload {
    public static final Type<DrawSaveMapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "draw_save_map"));

    public static final StreamCodec<FriendlyByteBuf, DrawSaveMapPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DrawSaveMapPayload decode(FriendlyByteBuf buf) {
            int[] pixels = buf.readVarIntArray();
            String name = buf.readUtf(); // 作品名字
            return new DrawSaveMapPayload(pixels, name);
        }

        @Override
        public void encode(FriendlyByteBuf buf, DrawSaveMapPayload payload) {
            buf.writeVarIntArray(payload.pixels());
            buf.writeUtf(payload.itemName() != null ? payload.itemName() : "");
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
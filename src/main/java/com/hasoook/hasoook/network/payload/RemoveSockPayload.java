package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 → 服务端：移除指定索引的糊脸袜子。
 */
public record RemoveSockPayload(int index) implements CustomPayload {

    public static final CustomPayload.Id<RemoveSockPayload> ID =
            new CustomPayload.Id<>(Hasoook.id("remove_sock"));

    public static final PacketCodec<PacketByteBuf, RemoveSockPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, RemoveSockPayload::index,
                    RemoveSockPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

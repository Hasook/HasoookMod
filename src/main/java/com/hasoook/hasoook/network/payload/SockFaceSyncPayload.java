package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 服务端 → 客户端：同步指定玩家的袜子糊脸数据。
 */
public record SockFaceSyncPayload(UUID targetUuid, String data) implements CustomPayload {

    public static final CustomPayload.Id<SockFaceSyncPayload> ID =
            new CustomPayload.Id<>(Hasoook.id("sock_face_sync"));

    public static final PacketCodec<PacketByteBuf, SockFaceSyncPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString),
                    SockFaceSyncPayload::targetUuid,
                    PacketCodecs.STRING,
                    SockFaceSyncPayload::data,
                    SockFaceSyncPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

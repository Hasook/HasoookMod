package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record QuestSyncPayload(String questId, String questName, boolean isEntity) implements CustomPacketPayload {
    public static final Type<QuestSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "quest_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.questId);
                        buf.writeUtf(payload.questName);
                        buf.writeBoolean(payload.isEntity);
                    },
                    buf -> new QuestSyncPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
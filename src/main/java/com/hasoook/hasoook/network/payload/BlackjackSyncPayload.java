package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BlackjackSyncPayload(
        int phase, List<Integer> playerCards, List<Integer> dealerCards,
        boolean dealerHidden, int multiplier, int playerScore, int dealerScore,
        int result, String message, boolean canDoubleDown, int baseStake,
        boolean playerIsDealer, int villagerBudget, boolean dealerDrawing
) implements CustomPacketPayload {

    public static final Type<BlackjackSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "blackjack_sync"));

    public static final StreamCodec<FriendlyByteBuf, BlackjackSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.phase); buf.writeVarInt(p.playerCards.size());
                        for (int c : p.playerCards) buf.writeVarInt(c);
                        buf.writeVarInt(p.dealerCards.size());
                        for (int c : p.dealerCards) buf.writeVarInt(c);
                        buf.writeBoolean(p.dealerHidden); buf.writeVarInt(p.multiplier);
                        buf.writeVarInt(p.playerScore); buf.writeVarInt(p.dealerScore);
                        buf.writeVarInt(p.result); buf.writeUtf(p.message);
                        buf.writeBoolean(p.canDoubleDown); buf.writeVarInt(p.baseStake);
                        buf.writeBoolean(p.playerIsDealer); buf.writeVarInt(p.villagerBudget);
                        buf.writeBoolean(p.dealerDrawing);
                    },
                    buf -> {
                        int ph = buf.readVarInt(); int ps = buf.readVarInt();
                        List<Integer> pc = new ArrayList<>(); for (int i = 0; i < ps; i++) pc.add(buf.readVarInt());
                        int ds = buf.readVarInt();
                        List<Integer> dc = new ArrayList<>(); for (int i = 0; i < ds; i++) dc.add(buf.readVarInt());
                        return new BlackjackSyncPayload(ph, pc, dc,
                                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                                buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                                buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                                buf.readBoolean());
                    }
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

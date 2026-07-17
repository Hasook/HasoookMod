package com.hasoook.hasoook.network.payload;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record DouDiZhuSyncPayload(
        int phase,
        List<Integer> playerCards,       // 玩家手牌
        int p2CardCount,                 // 村民1手牌数
        int p3CardCount,                 // 村民2手牌数
        List<Integer> bottomCards,       // 底牌（叫地主后可见）
        int landlordIndex,               // 0=玩家, 1=村民1, 2=村民2; -1=未确定
        int currentPlayerIndex,          // 当前出牌人
        List<Integer> lastPlayedCards,   // 上一次出的牌
        int lastPlayedBy,                // 上一次出牌人 (-1=新一轮)
        String lastPlayedType,           // 上一次出的牌型描述
        String message,                  // 状态消息
        String villager1Name,            // 村民1名字
        String villager2Name,            // 村民2名字
        int villager1Budget,             // 村民1资金
        int villager2Budget,             // 村民2资金
        int baseStake,                   // 底注
        int multiplier,                  // 当前倍率
        int result,                      // 0=无, 1=地主赢, 2=农民赢
        int bidMultiplier,               // 叫地主倍率(1/2/3)
        boolean bottomRevealed,          // 底牌是否已揭示
        int lastActorIdx                 // 最近一次动作执行者 (0/1/2, -1=无)
) implements CustomPacketPayload {

    public static final Type<DouDiZhuSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "doudizhu_sync"));

    public static final StreamCodec<FriendlyByteBuf, DouDiZhuSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.phase);
                        writeIntList(buf, p.playerCards);
                        buf.writeVarInt(p.p2CardCount);
                        buf.writeVarInt(p.p3CardCount);
                        writeIntList(buf, p.bottomCards);
                        buf.writeVarInt(p.landlordIndex);
                        buf.writeVarInt(p.currentPlayerIndex);
                        writeIntList(buf, p.lastPlayedCards);
                        buf.writeVarInt(p.lastPlayedBy);
                        buf.writeUtf(p.lastPlayedType);
                        buf.writeUtf(p.message);
                        buf.writeUtf(p.villager1Name);
                        buf.writeUtf(p.villager2Name);
                        buf.writeVarInt(p.villager1Budget);
                        buf.writeVarInt(p.villager2Budget);
                        buf.writeVarInt(p.baseStake);
                        buf.writeVarInt(p.multiplier);
                        buf.writeVarInt(p.result);
                        buf.writeVarInt(p.bidMultiplier);
                        buf.writeBoolean(p.bottomRevealed);
                        buf.writeVarInt(p.lastActorIdx);
                    },
                    buf -> {
                        int phase = buf.readVarInt();
                        List<Integer> pc = readIntList(buf);
                        int p2c = buf.readVarInt();
                        int p3c = buf.readVarInt();
                        List<Integer> bc = readIntList(buf);
                        int li = buf.readVarInt();
                        int cp = buf.readVarInt();
                        List<Integer> lp = readIntList(buf);
                        int lb = buf.readVarInt();
                        String lt = buf.readUtf();
                        String msg = buf.readUtf();
                        String v1n = buf.readUtf();
                        String v2n = buf.readUtf();
                        int v1b = buf.readVarInt();
                        int v2b = buf.readVarInt();
                        int bs = buf.readVarInt();
                        int mult = buf.readVarInt();
                        int res = buf.readVarInt();
                        int bm = buf.readVarInt();
                        boolean br = buf.readBoolean();
                        int la = buf.readVarInt();
                        return new DouDiZhuSyncPayload(phase, pc, p2c, p3c, bc, li, cp, lp, lb, lt, msg,
                                v1n, v2n, v1b, v2b, bs, mult, res, bm, br, la);
                    }
            );

    private static void writeIntList(FriendlyByteBuf buf, List<Integer> list) {
        buf.writeVarInt(list.size());
        for (int v : list) buf.writeVarInt(v);
    }

    private static List<Integer> readIntList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(buf.readVarInt());
        return list;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

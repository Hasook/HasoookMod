package com.hasoook.hasoook.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

public record ConsumeDyePayload(List<String> itemIds) implements CustomPacketPayload {

    // 注册标识符
    public static final Type<ConsumeDyePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("hasoook", "consume_dye"));

    // 编解码器
    public static final StreamCodec<FriendlyByteBuf, ConsumeDyePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        // 写入列表大小
                        buf.writeInt(payload.itemIds.size());
                        // 循环写入每个字符串 ID
                        for (String id : payload.itemIds) {
                            buf.writeUtf(id);
                        }
                    },
                    buf -> {
                        // 读取列表大小
                        int size = buf.readInt();
                        List<String> list = new ArrayList<>(size);
                        // 循环读取每个字符串 ID
                        for (int i = 0; i < size; i++) {
                            list.add(buf.readUtf());
                        }
                        return new ConsumeDyePayload(list);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
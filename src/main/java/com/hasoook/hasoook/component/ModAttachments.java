package com.hasoook.hasoook.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "hasoook");

    // 修改这里：使用 .fieldOf() 包装为 MapCodec
    public static final Supplier<AttachmentType<String>> FILTERED_SOUND_ID = ATTACHMENT_TYPES.register(
            "filtered_sound_id",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING.fieldOf("sound_id")).build()
    );
    // 标记生物头部是否已被路易剪刀移除
    // serialize 仅持久化到磁盘，sync 才是网络同步到客户端！
    public static final Supplier<AttachmentType<Boolean>> HEAD_REMOVED = ATTACHMENT_TYPES.register(
            "head_removed",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("head_removed"))
                    .sync(StreamCodec.of(RegistryFriendlyByteBuf::writeBoolean, RegistryFriendlyByteBuf::readBoolean))
                    .build()
    );

    // 标记被移植到无头生物上的头部类型（存储实体类型ID字符串，空字符串表示无移植）
    public static final Supplier<AttachmentType<String>> TRANSPLANTED_HEAD_TYPE = ATTACHMENT_TYPES.register(
            "transplanted_head_type",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("transplanted_head_type"))
                    .sync(StreamCodec.of(
                            (buf, val) -> buf.writeUtf(val != null ? val : ""),
                            buf -> buf.readUtf()
                    ))
                    .build()
    );

    // 被移植的玩家头的 UUID 字符串（仅当移植来源是 player 时才有值，用于皮肤查询）
    public static final Supplier<AttachmentType<String>> TRANSPLANTED_HEAD_PLAYER_UUID = ATTACHMENT_TYPES.register(
            "transplanted_head_player_uuid",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("transplanted_head_player_uuid"))
                    .sync(StreamCodec.of(
                            (buf, val) -> buf.writeUtf(val != null ? val : ""),
                            buf -> buf.readUtf()
                    ))
                    .build()
    );

    // 被移植的玩家头的名称（仅当移植来源是 player 时才有值，用于物品显示名称）
    public static final Supplier<AttachmentType<String>> TRANSPLANTED_HEAD_PLAYER_NAME = ATTACHMENT_TYPES.register(
            "transplanted_head_player_name",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("transplanted_head_player_name"))
                    .sync(StreamCodec.of(
                            (buf, val) -> buf.writeUtf(val != null ? val : ""),
                            buf -> buf.readUtf()
                    ))
                    .build()
    );

    // 铜傀儡战斗模式 — 标记铜傀儡是否处于战斗状态
    public static final Supplier<AttachmentType<Boolean>> COPPER_GOLEM_BATTLE_MODE = ATTACHMENT_TYPES.register(
            "copper_golem_battle_mode",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL.fieldOf("battle_mode"))
                    .sync(StreamCodec.of(RegistryFriendlyByteBuf::writeBoolean, RegistryFriendlyByteBuf::readBoolean))
                    .build()
    );

    // 袜子糊脸数据 — 逗号分隔的 packed int 列表
    // 每个 int: 高16位=贴图索引，低16位=剩余tick；空字符串=无效果
    public static final Supplier<AttachmentType<String>> SOCK_FACE = ATTACHMENT_TYPES.register(
            "sock_face",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("sock_face"))
                    .sync(StreamCodec.of(
                            (RegistryFriendlyByteBuf buf, String val) -> buf.writeUtf(val != null ? val : ""),
                            RegistryFriendlyByteBuf::readUtf
                    ))
                    .build()
    );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
package com.hasoook.hasoook.event.chat;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.util.ClientSoundHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;

/**
 * 当玩家移植了生物头后，发送聊天消息会被替换为该生物的"叫声"。
 * <p>
 * 叫声文字来源（全自动，无需手动穷举）：
 * <ol>
 *   <li>构造 {@code entity.<name>.ambient} 声音 ID，
 *       通过 {@link ClientSoundHelper#getAccurateSubtitle(Identifier)} 获取字幕文本，
 *       解析"生物名：声音描述"格式，提取冒号后的声音描述（如"嘶鸣"、"哞"、"嘶叫"）</li>
 *   <li>兜底：使用生物的本地化名称（如"苦力怕"、"羊驼"）</li>
 * </ol>
 * <p>
 * 替换规则：原消息中的每个"字母"字符（中文汉字、英文字母等）
 * 会被替换为叫声字符串中的下一个字符（循环使用），标点、数字、空格保持不变。
 * <p>
 * 例如：马（字幕"马：嘶鸣"→提取"嘶鸣"）→ 玩家发送"这是什么东西？" → "嘶鸣嘶鸣嘶鸣？"
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class MobHeadChatEvent {

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 检查玩家是否移植了生物头
        String transplantType = player.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
        if (transplantType == null || transplantType.isEmpty()) return;

        // 自动解析叫声文字
        String sound = resolveSoundText(transplantType);
        if (sound == null || sound.isEmpty()) return;

        // 获取原始消息文本并转换
        String original = event.getOriginalMessage();
        String transformed = transformMessage(original, sound);

        // 替换消息
        event.setMessage(transformed);
    }

    /**
     * 根据实体类型 ID 自动解析叫声文字。
     * <ol>
     *   <li>构造 {@code entity.<path>.ambient} 声音 → 查字幕 → 提取冒号后的描述</li>
     *   <li>兜底：用生物的本地化名称（客户端可解析翻译）</li>
     * </ol>
     */
    private static String resolveSoundText(String typeId) {
        Identifier entityId = Identifier.tryParse(typeId);
        if (entityId == null) return null;

        // --- 1. 尝试从 ambient 声音字幕中提取 ---
        String subtitleSound = extractFromAmbientSubtitle(entityId);
        if (subtitleSound != null) return subtitleSound;

        // --- 2. 兜底：使用生物的本地化名称 ---
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(entityId);
        if (entityType != null) {
            return entityType.getDescription().getString();
        }

        return null;
    }

    /**
     * 尝试从实体的 ambient 声音事件字幕中提取声音描述。
     * <p>
     * 流程：实体 ID → 构造 {@code entity.<name>.ambient} 声音 ID
     * → {@link ClientSoundHelper#getAccurateSubtitle(Identifier)} 获取字幕
     * → 解析"生物名：描述"格式 → 返回描述部分
     *
     * @param entityId 实体注册表 ID（如 {@code minecraft:horse}）
     * @return 提取的声音描述文本，失败返回 {@code null}
     */
    private static String extractFromAmbientSubtitle(Identifier entityId) {
        // 构造 ambient 声音事件 ID：entity.<name>.ambient
        Identifier soundId = Identifier.fromNamespaceAndPath(
                entityId.getNamespace(),
                "entity." + entityId.getPath() + ".ambient"
        );

        // 通过 ClientSoundHelper 获取该声音的字幕（自动处理翻译）
        Component subtitle = ClientSoundHelper.getAccurateSubtitle(soundId);
        if (subtitle == null) return null;

        String subtitleText = subtitle.getString();

        // 解析字幕格式："生物名：声音描述" → 提取"声音描述"
        // 中文 MC 使用全角冒号 "："，其他语言可能用半角 ":"
        int colonIndex = subtitleText.indexOf('：');
        if (colonIndex == -1) colonIndex = subtitleText.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < subtitleText.length()) {
            return subtitleText.substring(colonIndex + 1).trim();
        }

        // 没有冒号则使用整个字幕文本
        return subtitleText;
    }

    /**
     * 将消息文本中的每个字母字符替换为叫声字符串中的下一个字符（循环使用）。
     * 空格、标点、数字、emoji 等非字母字符保持不变。
     * <p>
     * 例如：sound="嘶鸣", message="这是什么东西？" → "嘶鸣嘶鸣嘶鸣？"
     *
     * @param message 原始消息
     * @param sound   叫声字符串（作为字符循环池使用）
     * @return 转换后的消息
     */
    private static String transformMessage(String message, String sound) {
        StringBuilder sb = new StringBuilder(message.length());
        int soundIndex = 0;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(sound.charAt(soundIndex % sound.length()));
                soundIndex++;
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }
}

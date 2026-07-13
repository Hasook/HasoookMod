package com.hasoook.hasoook.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClientSoundHelper {

    // 获取声音的官方精准字幕
    public static Component getAccurateSubtitle(Identifier soundId) {
        // 从客户端的声音管理器中获取该声音在 sounds.json 中的定义
        WeighedSoundEvents soundEvents = Minecraft.getInstance().getSoundManager().getSoundEvent(soundId);

        // 如果该声音有配置官方字幕，直接返回（这会自动处理好所有翻译）
        if (soundEvents != null && soundEvents.getSubtitle() != null) {
            return soundEvents.getSubtitle();
        }

        return null; // 没有官方字幕则返回 null
    }
}
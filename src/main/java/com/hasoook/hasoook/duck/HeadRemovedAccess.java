package com.hasoook.hasoook.duck;

import org.jspecify.annotations.Nullable;

/**
 * 访问 LivingEntityRenderState 上由 LivingEntityRenderStateMixin 注入的斩首标记和移植头部信息。
 */
public interface HeadRemovedAccess {
    boolean hasoook$isHeadRemoved();
    void hasoook$setHeadRemoved(boolean value);

    @Nullable
    String hasoook$getTransplantedHeadType();
    void hasoook$setTransplantedHeadType(@Nullable String type);

    /** 获取被移植的玩家头的 UUID（仅 player 头有值，用于皮肤查询） */
    @Nullable
    String hasoook$getTransplantedPlayerUuid();
    void hasoook$setTransplantedPlayerUuid(@Nullable String uuid);

    /** 获取被移植的玩家头的名称（仅 player 头有值，用于皮肤查询） */
    @Nullable
    String hasoook$getTransplantedPlayerName();
    void hasoook$setTransplantedPlayerName(@Nullable String name);
}

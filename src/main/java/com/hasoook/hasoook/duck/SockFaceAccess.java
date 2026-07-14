package com.hasoook.hasoook.duck;

import org.jspecify.annotations.Nullable;

/**
 * 访问 LivingEntityRenderState 上由 LivingEntityRenderStateMixin 注入的袜子糊脸数据。
 * 用于 SockFaceLayer 在第三人称渲染时读取玩家脸上的袜子信息。
 */
public interface SockFaceAccess {
    /** 获取袜子糊脸数据（逗号分隔的 packed int 列表），空字符串表示无效果 */
    String hasoook$getSockFaceData();

    /** 设置袜子糊脸数据 */
    void hasoook$setSockFaceData(String data);
}

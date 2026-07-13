package com.hasoook.hasoook.manager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DrawingManager {
    // 使用 LRU 缓存机制，最多保存 100 张，防止服务器内存泄漏
    private static final int MAX_CACHE_SIZE = 100;
    private static final Map<UUID, byte[]> DRAWINGS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, byte[]> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public static void save(UUID uuid, byte[] data) {
        DRAWINGS.put(uuid, data);
    }

    public static byte[] get(UUID uuid) {
        return DRAWINGS.get(uuid);
    }
}
package com.hasoook.hasoook.component;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理袜子糊脸数据（替代 NeoForge 的 AttachmentType）。
 * 服务端存储，通过 SockFaceSyncPayload 同步到客户端。
 */
public class SockFaceData {

    private static final Map<UUID, String> SERVER_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, String> CLIENT_DATA = new ConcurrentHashMap<>();

    public static String getSockFace(Entity entity) {
        if (entity.getEntityWorld().isClient()) {
            return CLIENT_DATA.getOrDefault(entity.getUuid(), "");
        }
        return SERVER_DATA.getOrDefault(entity.getUuid(), "");
    }

    public static void setSockFace(Entity entity, String data) {
        if (entity.getEntityWorld().isClient()) return;
        if (data == null || data.isEmpty()) {
            SERVER_DATA.remove(entity.getUuid());
        } else {
            SERVER_DATA.put(entity.getUuid(), data);
        }
    }

    public static void readFromNbt(UUID uuid, String data) {
        if (data == null || data.isEmpty()) {
            SERVER_DATA.remove(uuid);
        } else {
            SERVER_DATA.put(uuid, data);
        }
    }

    public static String writeToNbt(UUID uuid) {
        return SERVER_DATA.getOrDefault(uuid, "");
    }

    public static void updateClientCache(UUID uuid, String data) {
        if (data == null || data.isEmpty()) {
            CLIENT_DATA.remove(uuid);
        } else {
            CLIENT_DATA.put(uuid, data);
        }
    }

    public static void clearClientCache(UUID uuid) {
        CLIENT_DATA.remove(uuid);
    }
}

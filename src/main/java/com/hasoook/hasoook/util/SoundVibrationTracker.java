package com.hasoook.hasoook.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class SoundVibrationTracker {
    // 声音缓存
    private static final List<SoundRecord> RECENT_SOUNDS = new ArrayList<>();
    // 待办的振动事件队列（重点）
    private static final List<PendingEvent> PENDING_EVENTS = new ArrayList<>();

    // 绕过 Mixin 拦截的开关
    public static final ThreadLocal<Boolean> BYPASS_MIXIN = ThreadLocal.withInitial(() -> false);

    private static long serverTickCounter = 0;

    private record SoundRecord(String soundId, int entityId, BlockPos pos, long tick) {}

    // 记录被扣留的振动事件
    public record PendingEvent(ServerLevel level, VibrationSystem.Listener listener, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos, String requiredSoundId) {}

    // 实体发出的声音
    @SubscribeEvent
    public static void onSoundEntity(PlayLevelSoundEvent.AtEntity event) {
        if (event.getLevel().isClientSide()) return;
        event.getSound().unwrapKey().ifPresent(key -> {
            RECENT_SOUNDS.add(new SoundRecord(key.identifier().toString(), event.getEntity().getId(), event.getEntity().blockPosition(), serverTickCounter));
        });
    }

    // 特定坐标发出的声音
    @SubscribeEvent
    public static void onSoundPos(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel().isClientSide()) return;
        event.getSound().unwrapKey().ifPresent(key -> {
            RECENT_SOUNDS.add(new SoundRecord(key.identifier().toString(), -1, BlockPos.containing(event.getPosition()), serverTickCounter));
        });
    }

    // 提供给 Mixin 调用的方法：扣留当前的振动
    public static void queuePendingEvent(ServerLevel level, VibrationSystem.Listener listener, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos, String requiredSoundId) {
        PENDING_EVENTS.add(new PendingEvent(level, listener, gameEvent, context, pos, requiredSoundId));
    }

    // 在 Tick 末尾统一结算
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        serverTickCounter++;

        // 1. 处理被扣留的振动
        if (!PENDING_EVENTS.isEmpty()) {
            BYPASS_MIXIN.set(true); // 打开绕过开关，防止无限套娃拦截

            for (PendingEvent p : PENDING_EVENTS) {
                Entity source = p.context().sourceEntity();
                BlockPos eventPos = BlockPos.containing(p.pos());

                // 此时 Tick 即将结束，如果伴随了声音，RECENT_SOUNDS 里一定已经有记录了！
                if (hasRecentSound(source, eventPos, p.requiredSoundId())) {
                    // 声音核对成功！手动原样重新触发原版的振动逻辑
                    p.listener().handleGameEvent(p.level(), p.gameEvent(), p.context(), p.pos());
                }
            }

            BYPASS_MIXIN.set(false); // 关闭绕过开关
            PENDING_EVENTS.clear();
        }

        // 2. 清理过期声音（保留最近 3 tick 即可，因为主要在同 tick 结算）
        if (!RECENT_SOUNDS.isEmpty()) {
            RECENT_SOUNDS.removeIf(record -> (serverTickCounter - record.tick()) > 3);
        }
    }

    /**
     * 核对声音逻辑
     */
    private static boolean hasRecentSound(Entity entity, BlockPos pos, String requiredSoundId) {
        if (RECENT_SOUNDS.isEmpty()) return false;

        for (SoundRecord record : RECENT_SOUNDS) {
            if (!record.soundId().equals(requiredSoundId)) continue;

            // 1. 优先匹配实体ID (最精准)
            if (entity != null && record.entityId() == entity.getId()) {
                return true;
            }
            // 2. 降级匹配坐标 (容差扩大到直线距离8格以内，即 64.0)
            if (pos != null && record.pos().distSqr(pos) <= 64.0) {
                return true;
            }
        }
        return false;
    }
}
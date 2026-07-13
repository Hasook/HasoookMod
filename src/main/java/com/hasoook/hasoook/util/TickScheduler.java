package com.hasoook.hasoook.util;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TickScheduler {
    private static final List<Task> TASKS = new ArrayList<>();

    public static void schedule(ServerLevel level, int delay, Runnable runnable) {
        TASKS.add(new Task(level, level.getGameTime() + delay, runnable));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        long time = level.getGameTime();

        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();

            if (task.level == level && task.executeTime <= time) {
                task.runnable.run();
                iterator.remove();
            }
        }
    }

    private record Task(ServerLevel level, long executeTime, Runnable runnable) {
    }
}
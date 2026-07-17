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

        // 先收集到期任务，避免在执行任务时添加新任务导致并发修改
        List<Runnable> toRun = new ArrayList<>();

        Iterator<Task> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.level == level && task.executeTime <= time) {
                toRun.add(task.runnable);
                iterator.remove();
            }
        }

        // 在迭代完成后再执行任务
        for (Runnable runnable : toRun) {
            runnable.run();
        }
    }

    private record Task(ServerLevel level, long executeTime, Runnable runnable) {
    }
}

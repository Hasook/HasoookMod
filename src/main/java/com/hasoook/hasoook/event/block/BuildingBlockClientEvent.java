package com.hasoook.hasoook.event.block;

import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(value = Dist.CLIENT)
public class BuildingBlockClientEvent {

    private static final Map<UUID, Float> SLEEP_YAW = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (!living.hasEffect(ModEffects.DISABILITY)) {
                SLEEP_YAW.remove(living.getUUID());
                continue;
            }

            if (e.getType() == EntityType.PLAYER) {
                living.setPose(Pose.SLEEPING);
                Float yaw = SLEEP_YAW.get(living.getUUID());
                if (yaw == null) {
                    yaw = living.yBodyRot;
                    SLEEP_YAW.put(living.getUUID(), yaw);
                }
                living.yBodyRot = yaw;
                living.yBodyRotO = yaw;
                living.yHeadRot = yaw;
                living.yHeadRotO = yaw;
                living.walkAnimation.setSpeed(0);
                living.walkAnimation.position(0);
            }

        }
    }

}

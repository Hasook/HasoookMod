package com.hasoook.hasoook.event.block;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.custom.PhantomLampBlock;
import com.hasoook.hasoook.block.entity.custom.PhantomLampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class PhantomLampSleepEvent {

    @SubscribeEvent
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        // Only override the NOT_SAFE (monsters nearby) problem
        if (event.getProblem() != Player.BedSleepingProblem.NOT_SAFE) {
            return;
        }

        ServerPlayer player = event.getEntity();
        BlockPos playerPos = player.blockPosition();
        Level level = player.level();

        // Check for a functional Phantom Lamp within 5 blocks
        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-5, -5, -5),
                playerPos.offset(5, 5, 5))) {
            if (level.getBlockState(pos).getBlock() instanceof PhantomLampBlock) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PhantomLampBlockEntity lampEntity && lampEntity.canSleep()) {
                    event.setProblem(null); // Allow sleeping — lamp protects you!
                    return;
                }
            }
        }
    }
}

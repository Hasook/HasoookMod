package com.hasoook.hasoook.block.entity;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.block.entity.custom.MobHeadBlockEntity;
import com.hasoook.hasoook.block.entity.custom.PhantomLampBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hasoook.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhantomLampBlockEntity>> PHANTOM_LAMP =
            BLOCK_ENTITIES.register("phantom_lamp",
                    () -> new BlockEntityType<>(
                            PhantomLampBlockEntity::new,
                            Set.of(ModBlocks.PHANTOM_LAMP.get())
                    ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobHeadBlockEntity>> MOB_HEAD =
            BLOCK_ENTITIES.register("mob_head",
                    () -> new BlockEntityType<>(
                            MobHeadBlockEntity::new,
                            Set.of(ModBlocks.MOB_HEAD.get())
                    ));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

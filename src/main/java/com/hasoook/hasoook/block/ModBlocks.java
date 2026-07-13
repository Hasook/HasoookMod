package com.hasoook.hasoook.block;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.custom.MobHeadBlock;
import com.hasoook.hasoook.block.custom.PhantomLampBlock;
import com.hasoook.hasoook.item.ModItems; // 假设你有一个 ModItems 类用来注册物品
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hasoook.MOD_ID);

    public static final DeferredBlock<Block> PHANTOM_LAMP = registerBlock("phantom_lamp",
            properties -> new PhantomLampBlock(properties
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(0.3F)
                    .sound(SoundType.LANTERN)
                    .lightLevel(state -> 12)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
            ));

    // 生物头方块 — 不使用 registerBlock 辅助方法，因为其 BlockItem 在 ModItems 中单独注册（使用自定义 MobHeadItem）
    public static final DeferredBlock<Block> MOB_HEAD = BLOCKS.registerBlock("mob_head",
            properties -> new MobHeadBlock(properties
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(1.0F)
                    .sound(SoundType.BONE_BLOCK)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
            ));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> function) {
        DeferredBlock<T> toReturn = BLOCKS.registerBlock(name, function);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.registerItem(name, properties -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
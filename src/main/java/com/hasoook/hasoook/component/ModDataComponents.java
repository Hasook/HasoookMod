package com.hasoook.hasoook.component;

import com.hasoook.hasoook.Hasoook;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hasoook.MOD_ID);

    // 深遭状态
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SEVOWER_STATE = register("sevower_state",
            builder -> builder.persistent(Codec.STRING));

    // 回声瓶记录声音
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> RECORDED_SOUND = register("recorded_sound",
            builder -> builder.persistent(Codec.STRING));

    // 桶装过的流体
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LAST_FLUID =
            register("last_fluid", builder -> builder.persistent(Codec.STRING));

    // 液体状态进度值
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PROGRESS =
            register("progress", builder -> builder.persistent(Codec.INT));

    // 幻翼之灯状态: pristine / broken / repaired
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PHANTOM_LAMP_STATE =
            register("phantom_lamp_state", builder -> builder.persistent(Codec.STRING));

    // 生物头物品对应的实体类型
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> MOB_HEAD_TYPE =
            register("mob_head_type", builder -> builder.persistent(Codec.STRING));

    // 玩家头物品对应的玩家名（仅 player 头有值）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> HEAD_OWNER_NAME =
            register("head_owner_name", builder -> builder.persistent(Codec.STRING));

    // 玩家头物品对应的玩家 UUID 字符串（仅 player 头有值，用于皮肤查询）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> HEAD_OWNER_UUID =
            register("head_owner_uuid", builder -> builder.persistent(Codec.STRING));

    // 盔甲架剑存储的装备内容
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> ARMOR_STAND_SWORD_CONTENTS =
            register("armor_stand_sword_contents", builder -> builder.persistent(ItemContainerContents.CODEC));

    // 盔甲架剑当前选中的装备槽位 (0-3, -1 = 未选中)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED_SLOT =
            register("selected_slot", builder -> builder.persistent(Codec.INT));

    // 生物头是否可接头（通过黏液球合成获得）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> MOB_HEAD_ATTACHABLE =
            register("mob_head_attachable", builder -> builder.persistent(Codec.BOOL));

    // 袜子磨损值（玩家穿着走路/疾跑时累积）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SOCKS_WEAR =
            register("socks_wear", builder -> builder.persistent(Codec.INT));

    // 蓄电铜剑蓄电值（避雷针被雷劈中 + 下方铜箱子 = +10 蓄电）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_SWORD_CHARGE =
            register("charged_copper_sword_charge", builder -> builder.persistent(Codec.INT));

    // 蓄电铜剑闪电链剩余渲染 tick 数（>0 时客户端渲染闪电链）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_SWORD_CHAIN_TICKS =
            register("charged_copper_sword_chain_ticks", builder -> builder.persistent(Codec.INT));

    // 蓄电铜剑闪电链起始位置（Long 编码的 BlockPos）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CHARGED_COPPER_SWORD_CHAIN_POS =
            register("charged_copper_sword_chain_pos", builder -> builder.persistent(Codec.LONG));

    // 蓄电铜镐链式破坏方块位置列表（分号分隔 "x,y,z;x,y,z;..."）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CHARGED_COPPER_PICKAXE_CHAIN_BLOCKS =
            register("charged_copper_pickaxe_chain_blocks", builder -> builder.persistent(Codec.STRING));

    // 蓄电铜镐当前生效的工具档位（避免每 tick 重建 Tool 组件）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_PICKAXE_TOOL_CHARGE =
            register("charged_copper_pickaxe_tool_charge", builder -> builder.persistent(Codec.INT));

    // 蓄电大招蓄力计数（每 tick +1，释放时清零）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_CHARGING_POWER =
            register("charged_copper_charging_power", builder -> builder.persistent(Codec.INT));

    // 闪电冲击波剩余 tick（>0 时渲染闪电圆圈）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_SHOCKWAVE_TICKS =
            register("charged_copper_shockwave_ticks", builder -> builder.persistent(Codec.INT));

    // 闪电冲击波最大半径（Double，单位格）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> CHARGED_COPPER_SHOCKWAVE_RADIUS =
            register("charged_copper_shockwave_radius", builder -> builder.persistent(Codec.DOUBLE));

    // 蓄力开始时锁定的蓄电上限（避免蓄力过程中上限随消耗递减）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGED_COPPER_CHARGE_CAP =
            register("charged_copper_charge_cap", builder -> builder.persistent(Codec.INT));

    // 积木附着数量（积木 + 鞋子合成，最多可塞16个，左键挥动可甩出积木）
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BUILDING_BLOCK_ATTACHED =
            register("building_block_attached", builder -> builder.persistent(Codec.INT));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator
    ) {
        return DATA_COMPONENT_TYPES.register(name,
                () -> builderOperator.apply(DataComponentType.builder()).build());
    }

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
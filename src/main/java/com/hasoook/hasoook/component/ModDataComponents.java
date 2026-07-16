package com.hasoook.hasoook.component;

import com.hasoook.hasoook.Hasoook;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModDataComponents {
    // 袜子磨损值（玩家穿着走路/疾跑时累积）
    public static final ComponentType<Integer> SOCKS_WEAR = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Hasoook.id("socks_wear"),
            ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    public static void initialize() {
        // Trigger class loading
    }
}

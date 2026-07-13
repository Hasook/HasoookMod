package com.hasoook.hasoook.item.custom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public class NetherStarSpearItem extends Item {
    public NetherStarSpearItem(Properties properties) {
        super(properties
                .spear(ToolMaterial.NETHERITE, 1.4F, 1.2F, 1.0F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F)
                .durability(1521) // 耐久度
                .repairable(Items.NETHER_STAR) // 修复材料
                .rarity(Rarity.EPIC)); // 稀有度 UNCOMMON黄色，RARE青色，EPIC紫色
    }
}

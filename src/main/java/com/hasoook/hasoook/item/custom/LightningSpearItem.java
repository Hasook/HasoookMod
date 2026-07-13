package com.hasoook.hasoook.item.custom;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public class LightningSpearItem extends Item {
    public LightningSpearItem(Properties properties) {
        super(properties
                .spear(ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
                .durability(1521) // 耐久度
                .repairable(Items.NETHER_STAR) // 修复材料
                .rarity(Rarity.RARE)); // 稀有度 UNCOMMON黄色，RARE青色，EPIC紫色
    }
}

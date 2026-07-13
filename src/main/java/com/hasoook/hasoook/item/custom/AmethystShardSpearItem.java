package com.hasoook.hasoook.item.custom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import org.jspecify.annotations.NonNull;

public class AmethystShardSpearItem extends Item {
    public AmethystShardSpearItem(Properties properties) {
        super(properties.spear(ToolMaterial.STONE, 0.75F, 0.82F, 0.7F, 4.5F, 10.0F, 9.0F, 5.1F, 13.75F, 4.6F)
                .durability(121)
                .repairable(Items.AMETHYST_SHARD));
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F + level.random.nextFloat() * 0.4F);
        }
        super.hurtEnemy(stack, target, attacker);
    }
}

package com.hasoook.hasoook.item.custom;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

public class EndCrystalSpearItem extends Item {
    public EndCrystalSpearItem(Properties properties) {
        super(properties.spear(ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
                .durability(1) // 耐久度
                .repairable(Items.AMETHYST_SHARD) // 修复材料
                .rarity(Rarity.RARE) // 稀有度 UNCOMMON黄色，RARE青色，EPIC紫色
        );
    }

    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return true;  // 始终显示附魔光效
    }

    @Override
    public void hurtEnemy(@NonNull ItemStack stack, @NonNull LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            BlockState state = Blocks.AMETHYST_CLUSTER.defaultBlockState();
            for (int i = 0; i < 20; i++) {
                double dx = (level.random.nextDouble() - 0.5) * 0.2;
                double dy = (level.random.nextDouble() - 0.5) * 0.2;
                double dz = (level.random.nextDouble() - 0.5) * 0.2;

                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        target.getX(),
                        target.getY() + 0.1,
                        target.getZ(),
                        1,
                        dx,
                        dy,
                        dz,
                        0.0
                );
            }
        }
        super.hurtEnemy(stack, target, attacker);
    }
}
package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CopperGolemBattleChipItem extends Item {

    public CopperGolemBattleChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CopperGolem golem)) {
            return InteractionResult.PASS;
        }

        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean currentMode = golem.getData(ModAttachments.COPPER_GOLEM_BATTLE_MODE);
        boolean newMode = !currentMode;
        golem.setData(ModAttachments.COPPER_GOLEM_BATTLE_MODE, newMode);

        // Sound feedback
        if (newMode) {
            player.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.NEUTRAL, 1.0F, 1.5F);
        } else {
            player.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }

        // Particle feedback
        if (player.level() instanceof ServerLevel serverLevel) {
            if (newMode) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        golem.getX(), golem.getY() + golem.getBbHeight() * 0.7, golem.getZ(),
                        15, 0.4, 0.4, 0.4, 0.08);
            } else {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        golem.getX(), golem.getY() + golem.getBbHeight() * 0.7, golem.getZ(),
                        10, 0.3, 0.3, 0.3, 0.05);
            }
        }

        return InteractionResult.SUCCESS;
    }
}

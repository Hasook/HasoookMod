package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.RecoveryClockItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;

public class EchoArrowProjectile extends AbstractArrow {

    public EchoArrowProjectile(EntityType<? extends EchoArrowProjectile> type, Level level) {
        super(type, level);
    }

    public EchoArrowProjectile(Level level, LivingEntity shooter, ItemStack stack, @Nullable ItemStack weapon) {
        super(ModEntities.ECHO_ARROW.get(), shooter, level, stack, weapon);
    }

    public EchoArrowProjectile(Level level, double x, double y, double z, ItemStack stack, @Nullable ItemStack weapon) {
        super(ModEntities.ECHO_ARROW.get(), x, y, z, level, stack, weapon);
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide() && !this.isInGround()) {
            this.level().addParticle(ParticleTypes.SCULK_CHARGE_POP, this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F, 0.0F);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (!(this.getOwner() instanceof ServerPlayer shooter)) {
            return;
        }

        if (!(result.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // 查找玩家是否拥有追溯时钟
        ItemStack clockStack = findRecoveryClock(shooter);
        if (clockStack.isEmpty()) {
            return;
        }

        // 绑定目标并启动记录
        RecoveryClockItem.bindTargetAndStart(shooter, target);
    }

    private ItemStack findRecoveryClock(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof RecoveryClockItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findRecoveryCompass(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.RECOVERY_COMPASS)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult p_481027_) {
        super.onHitBlock(p_481027_);
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.SCULK_SOUL, this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F, 0.0F);
        }
    }

    @Override
    protected @NonNull ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.ECHO_ARROW.get());
    }
}
package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.entity.custom.EchoArrowProjectile;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class EchoArrowItem extends ArrowItem {
    public EchoArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull AbstractArrow createArrow(@NonNull Level level, ItemStack ammo, @NonNull LivingEntity shooter, @Nullable ItemStack weapon) {
        return new EchoArrowProjectile(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public @NonNull Projectile asProjectile(@NonNull Level level, Position pos, ItemStack stack, @NonNull Direction direction) {
        return new EchoArrowProjectile(level,
                pos.x(),
                pos.y(),
                pos.z(),
                stack.copyWithCount(1),
                null);
    }
}
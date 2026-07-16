package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.custom.ThrownSockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class ThrowableSockItem extends SocksItem {

    public ThrowableSockItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return ActionResult.CONSUME;
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return false;

        int useTime = this.getMaxUseTime(stack, user) - remainingUseTicks;
        if (useTime < 5) return false;

        float power = Math.min(useTime / 20f, 1.0f);
        float speed = 0.2f + power * 0.6f;

        if (!world.isClient()) {
            ThrownSockEntity projectile = new ThrownSockEntity(player, world);
            projectile.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
            var look = player.getRotationVec(1.0f);
            projectile.setVelocity(look.x, look.y, look.z, speed, 1.0f);

            int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
            projectile.setWear(wear);

            world.spawnEntity(projectile);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS,
                    0.5f, 0.4f / (world.random.nextFloat() * 0.4f + 0.8f));

            stack.decrementUnlessCreative(1, player);
        }

        player.incrementStat(Stats.USED.getOrCreateStat(this));
        return false;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return;
        if (player.getEntityWorld().isClient()) return;

        int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
        int wearStage = SocksItem.getStage(wear);
        int texIndex = player.getRandom().nextInt(3);
        int seed = player.getRandom().nextInt(16);
        int packed = (texIndex << 24) | (wearStage << 16) | (seed << 12) | 2047;

        String cur = com.hasoook.hasoook.component.SockFaceData.getSockFace(target);
        String next = cur.isEmpty() ? String.valueOf(packed) : cur + "," + packed;
        com.hasoook.hasoook.component.SockFaceData.setSockFace(target, next);

        // 同步到客户端
        if (target instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            com.hasoook.hasoook.network.ModNetworkInit.syncSockFaceToPlayer(sp);
        }

        stack.decrementUnlessCreative(1, player);
        super.postHit(stack, target, attacker);
    }
}

package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.entity.custom.SevowerProjectile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class SevowerItem extends Item {
    private static final String STATE_EATING = "eating";
    private static final String STATE_ATTACKING = "attacking";

    public SevowerItem(Properties properties) {
        super(properties.axe(ToolMaterial.DIAMOND, 3.0F, -2.4F)
                .rarity(Rarity.RARE));
    }

    @Override
    public @NonNull InteractionResult use(Level level, Player player, @NonNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {

            // 潜行 → 切换模式
            if (player.isShiftKeyDown()) {
                toggleState(stack);

                boolean attacking = STATE_ATTACKING.equals(getState(stack));
                updateModel(stack, attacking ? 1.0F : 0.0F);

                String msg = attacking ? "当前状态: 进攻" : "当前状态: 进食";
                player.displayClientMessage(Component.literal(msg), true);

                return InteractionResult.SUCCESS_SERVER;
            }

            // 普通右键 → 进攻状态投掷
            if (STATE_ATTACKING.equals(getState(stack))) {
                if (level instanceof ServerLevel serverLevel) {
                    Projectile.spawnProjectileFromRotation(
                            (lvl, shooter, item) -> {
                                // 这里把 item 传进去
                                SevowerProjectile projectile = new SevowerProjectile(shooter, lvl, item);
                                projectile.setDamage(8.0F);
                                return projectile;
                            },
                            serverLevel,
                            stack,
                            player,
                            0.0F,
                            2.0F,
                            0.0F
                    );

                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }

                }

                return InteractionResult.SUCCESS_SERVER;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean isCorrectToolForDrops(@NonNull ItemStack stack, @NonNull BlockState state) {
        if (STATE_EATING.equals(getState(stack))) {
            return true; // 进食状态下当万能工具
        }

        return super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(@NonNull ItemStack stack, @NonNull BlockState state) {
        if (STATE_EATING.equals(getState(stack))) {
            return 6.0F; // 进食状态下效率为6
        }

        return super.getDestroySpeed(stack, state);
    }

    // 获取状态（进食/进攻）
    public static String getState(ItemStack stack) {
        return stack.getOrDefault(
                ModDataComponents.SEVOWER_STATE.get(),
                STATE_EATING
        );
    }

    // 设置状态
    public static void setState(ItemStack stack, String state) {
        stack.set(ModDataComponents.SEVOWER_STATE.get(), state);
    }

    // 切换状态
    public static void toggleState(ItemStack stack) {
        String current = getState(stack);
        String next = STATE_ATTACKING.equals(current) ? STATE_EATING : STATE_ATTACKING;
        setState(stack, next);
    }

    // 更新模型
    private static void updateModel(ItemStack stack, float value) {
        CustomModelData oldCmd = stack.getOrDefault(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData.EMPTY
        );

        List<Float> floats = new ArrayList<>(oldCmd.floats());
        if (floats.isEmpty()) floats.add(0F);

        floats.set(0, value);

        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        floats,
                        oldCmd.flags(),
                        oldCmd.strings(),
                        oldCmd.colors()
                )
        );
    }
}

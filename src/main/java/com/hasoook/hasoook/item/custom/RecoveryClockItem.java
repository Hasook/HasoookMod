package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.damage.ModDamageSources;
import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class RecoveryClockItem extends Item {

    private static final Map<UUID, TimeData> PLAYER_TIME_DATA = new HashMap<>(); // 玩家时间数据
    private static final int MAX_RECORD_TICKS = 180; // 最大记录时间

    private record SavedPosition(double x, double y, double z, float yRot, float xRot, ResourceKey<Level> dimension) {}

    /**
     * 时间状态数据
     */
    private static class TimeData {
        boolean isRecording = false; // 是否正在记录
        boolean isRewinding = false; // 是否正在回溯
        boolean skipNext = false;

        long lastTickTime = -1; // 防止多个物品多次触发

        UUID boundTarget = null; // 绑定的目标
        final LinkedList<SavedPosition> positions = new LinkedList<>(); // 位置历史记录
    }

    public RecoveryClockItem(Properties properties) {
        super(properties.rarity(Rarity.UNCOMMON)
                .stacksTo(1));
    }

    /**
     * 右键使用逻辑
     * 若有绑定目标且未在回溯：直接触发回溯
     * 潜行使用：取消记录
     * 未记录：开始记录
     * 记录中：开始回溯
     */
    @Override
    public @NonNull InteractionResult use(Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        UUID uuid = player.getUUID();
        TimeData data = PLAYER_TIME_DATA.computeIfAbsent(uuid, k -> new TimeData());

        LivingEntity boundEntity = getBoundEntity(serverPlayer, data);

        // 目标死亡则重置
        if (data.boundTarget != null && boundEntity == null) {
            resetData(serverPlayer, data, player.getItemInHand(hand));
            return InteractionResult.SUCCESS;
        }

        // 如果已有目标，直接进入回溯
        if (data.boundTarget != null && !data.isRecording && !data.isRewinding) {
            data.isRewinding = true;
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 潜行使用，取消回溯记录
        if (player.isShiftKeyDown()) {
            if (data.isRecording) {

                data.isRecording = false;
                data.positions.clear();
                data.boundTarget = null;

                updateModel(player.getItemInHand(hand), 0.0F, serverPlayer);

                if (level instanceof ServerLevel serverLevel) {

                    Entity particleEntity = boundEntity != null ? boundEntity : serverPlayer;

                    serverLevel.sendParticles(
                            ParticleTypes.SCULK_CHARGE_POP,
                            particleEntity.getX(),
                            particleEntity.getY() + 1,
                            particleEntity.getZ(),
                            20, 0.0D, 0.4D, 0.0D, 0.1D
                    );

                    serverPlayer.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, particleEntity.getX(), particleEntity.getY(), particleEntity.getZ(), 1.0F, 1.0F, serverLevel.getRandom().nextLong()));
                }

                return InteractionResult.SUCCESS;
            }
        }

        // 开始追溯记录
        if (!data.isRecording && !data.isRewinding) {

            data.isRecording = true;
            data.skipNext = false;
            data.positions.clear();

            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);

            saveEntityPosition(serverPlayer, data); // 统一用 Entity 的保存方法

            // 如果有绑定目标，则开始回溯目标
        } else if (data.isRecording) {
            data.isRecording = false;
            data.isRewinding = true;

            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        return super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(@NonNull ItemStack stack, ServerLevel level, @NonNull Entity entity, @Nullable EquipmentSlot slot) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        TimeData data = PLAYER_TIME_DATA.get(serverPlayer.getUUID());

        if (data == null || (!data.isRecording && !data.isRewinding)) {
            updateModel(stack, 0.0F, serverPlayer);
            return;
        }

        // 防止多次执行
        long currentTick = level.getGameTime();
        if (data.lastTickTime == currentTick) {
            float modelValue = (float) Math.min(8, data.positions.size() / 20);
            updateModel(stack, modelValue, serverPlayer);
            return;
        }
        data.lastTickTime = currentTick;

        LivingEntity boundEntity = getBoundEntity(serverPlayer, data);

        // 目标死亡，直接重置
        if (data.boundTarget != null && boundEntity == null) {
            resetData(serverPlayer, data, stack);
            return;
        }

        // 如果有绑定目标则是目标，否则是玩家自身
        LivingEntity targetEntity = boundEntity != null ? boundEntity : serverPlayer;

        if (data.isRecording) {
            saveEntityPosition(targetEntity, data);

            // 达到记录上限自动回溯
            if (data.positions.size() >= MAX_RECORD_TICKS) {
                data.isRecording = false;
                data.isRewinding = true;

                serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            float modelValue = (float) Math.min(8, data.positions.size() / 20);
            updateModel(stack, modelValue, serverPlayer);

        } else {
            if (!data.positions.isEmpty()) { // 正在回溯
                SavedPosition pos = data.positions.removeLast();
                rewindEntity(targetEntity, pos);

                float modelValue = (float) Math.min(8, data.positions.size() / 20);
                updateModel(stack, modelValue, serverPlayer);

            } else { // 回溯完成逻辑
                data.isRewinding = false;
                data.boundTarget = null;

                updateModel(stack, 0.0F, serverPlayer);

                // 随机给予负面效果
                giveRandomEffect(serverPlayer);

                level.playSound(null,
                        targetEntity.blockPosition(),
                        SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                        SoundSource.PLAYERS,
                        1.0f, 1.0f);

                level.sendParticles(
                        ParticleTypes.SCULK_CHARGE_POP,
                        targetEntity.getX(),
                        targetEntity.getY() + 1,
                        targetEntity.getZ(),
                        20, 0.0D, 0.5D, 0.0D, 0.1D);
            }
        }
        super.inventoryTick(stack, level, entity, slot);
    }

    /**
     * 随机给予负面效果
     */
    private static void giveRandomEffect(ServerPlayer player) {
        // 如果玩家有时间紊乱效果，则造成伤害
        if (player.hasEffect(ModEffects.TEMPORAL_DISTORTION_EFFECT)) {
            player.hurt(ModDamageSources.temporalCollapse(player.level()), player.getHealth());
            player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 0.8F);
            return;
        }

        RandomSource random = player.getRandom();
        int duration = 600; // 时长

        MobEffectInstance effect = switch (random.nextInt(5)) {
            case 0 -> new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true, true);
            case 1 -> new MobEffectInstance(MobEffects.HUNGER, duration, 0, false, true, true);
            case 2 -> new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, false, true, true);
            case 3 -> new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 0, false, true, true);
            default -> new MobEffectInstance(ModEffects.TEMPORAL_DISTORTION_EFFECT, duration, 0, false, true, true);
        };

        player.addEffect(effect);
    }

    /**
     * 获取当前绑定实体
     */
    private static LivingEntity getBoundEntity(ServerPlayer player, TimeData data) {
        if (data.boundTarget == null) return null;

        Entity e = player.level().getEntity(data.boundTarget);
        if (e instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    /**
     * 重置状态
     */
    private void resetData(ServerPlayer player, TimeData data, ItemStack stack) {
        data.isRecording = false;
        data.isRewinding = false;
        data.positions.clear();
        data.boundTarget = null;

        updateModel(stack, 0.0F, player);
    }

    /**
     * 保存当前位置到历史列表
     */
    private void saveEntityPosition(LivingEntity entity, TimeData data) {
        data.positions.addLast(new SavedPosition(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getXRot(),
                entity.level().dimension()
        ));
    }

    /**
     * 执行回溯
     */
    private void rewindEntity(LivingEntity entity, SavedPosition pos) {
        if (!(entity.level() instanceof ServerLevel currentLevel)) return;

        ServerLevel targetLevel = currentLevel.getServer().getLevel(pos.dimension());
        if (targetLevel == null) targetLevel = currentLevel;

        entity.fallDistance = 0;

        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurtMarked = true;
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));

            if (serverPlayer.level() == targetLevel) {
                Vec3 movement = new Vec3(
                        pos.x() - serverPlayer.getX(),
                        pos.y() - serverPlayer.getY(),
                        pos.z() - serverPlayer.getZ()
                );
                serverPlayer.setDeltaMovement(movement);
                serverPlayer.connection.teleport(pos.x(), pos.y(), pos.z(), pos.yRot(), pos.xRot());
            } else {
                serverPlayer.setDeltaMovement(Vec3.ZERO);
                serverPlayer.teleportTo(targetLevel, pos.x(), pos.y(), pos.z(), Set.of(), pos.yRot(), pos.xRot(), false);
            }
        }
        else {
            if (entity.level() == targetLevel) {
                entity.setPos(pos.x(), pos.y(), pos.z());
                entity.setYRot(pos.yRot());
                entity.setXRot(pos.xRot());
            } else {
                entity.teleportTo(targetLevel, pos.x(), pos.y(), pos.z(), Set.of(), pos.yRot(), pos.xRot(), false);
            }
        }

        spawnRewindParticles(targetLevel, entity, pos);

        targetLevel.playSound(null,
                BlockPos.containing(pos.x(), pos.y(), pos.z()),
                SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS,
                0.5f, 1.0f);
    }

    /**
     * 强制结束回溯
     */
    public static void forceStop(ServerPlayer player) {
        TimeData data = PLAYER_TIME_DATA.get(player.getUUID());
        if (data == null) return;

        ServerLevel level = player.level();
        LivingEntity target = getBoundEntity(player, data);
        LivingEntity particleEntity = target != null ? target : player;

        // 如果正在记录，视为取消记录
        if (data.isRecording) {
            level.sendParticles(
                    ParticleTypes.SCULK_CHARGE_POP,
                    particleEntity.getX(),
                    particleEntity.getY() + 1,
                    particleEntity.getZ(),
                    20,
                    0.0D, 0.4D, 0.0D,
                    0.1D
            );

            player.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, particleEntity.getX(), particleEntity.getY(), particleEntity.getZ(), 1.0F, 1.0F, level.getRandom().nextLong()));
        }

        // 如果正在回溯，视为回溯完成
        if (data.isRewinding) {
            level.sendParticles(
                    ParticleTypes.SCULK_CHARGE_POP,
                    particleEntity.getX(),
                    particleEntity.getY() + 1,
                    particleEntity.getZ(),
                    20,
                    0.0D, 0.5D, 0.0D,
                    0.1D
            );

            level.playSound(null, particleEntity.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);

            giveRandomEffect(player);
        }

        data.isRecording = false;
        data.isRewinding = false;
        data.positions.clear();
        data.boundTarget = null;
    }

    /**
     * 生成传送粒子
     */
    private void spawnRewindParticles(ServerLevel level, LivingEntity entity, SavedPosition pos) {
        // 获取碰撞箱尺寸
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        // 扩散范围
        double spreadXZ = width * 0.4;
        double spreadY = height * 0.4;

        // 粒子数量根据体积近似计算
        double volume = width * width * height;
        int count = (int) Mth.clamp(volume * 10, 8, 100);

        DustColorTransitionOptions cyanToBlackParticle =
                new DustColorTransitionOptions(0x00FFFF, 0x000000, 1.0F);

        level.sendParticles(cyanToBlackParticle, pos.x(), pos.y() + height * 0.5, pos.z(), count, spreadXZ, spreadY, spreadXZ, 0.0);
    }

    /**
     * 更新模型
     */
    private static void updateModel(ItemStack stack, float value, @Nullable ServerPlayer player) {
        CustomModelData oldCmd = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
        List<Float> floats = new ArrayList<>(oldCmd.floats());

        float oldValue = floats.isEmpty() ? -1f : floats.getFirst();

        if (oldValue == value) {
            return;
        }

        if (player != null && oldValue != -1f) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.LODESTONE_COMPASS_LOCK,
                    SoundSource.PLAYERS,
                    0.4f,
                    isPlayerRewinding(player) ? 1.2f : 1.0f);
        }

        if (floats.isEmpty()) floats.add(value);
        else floats.set(0, value);

        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(floats, oldCmd.flags(), oldCmd.strings(), oldCmd.colors()));
    }

    @Override
    public boolean shouldCauseReequipAnimation(@NonNull ItemStack oldStack, @NonNull ItemStack newStack, boolean slotChanged) {
        if (!slotChanged && oldStack.getItem() == newStack.getItem()) return false;
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    /**
     * 查询玩家是否正在记录时间
     */
    public static boolean isPlayerRecording(Player player) {
        TimeData data = PLAYER_TIME_DATA.get(player.getUUID());
        return data != null && data.isRecording;
    }

    /**
     * 查询玩家是否正在回溯
     */
    public static boolean isPlayerRewinding(Player player) {
        TimeData data = PLAYER_TIME_DATA.get(player.getUUID());
        return data != null && data.isRewinding;
    }

    /**
     * 清理数据
     */
    public static void clearPlayerData(UUID uuid) {
        PLAYER_TIME_DATA.remove(uuid);
    }

    /**
     * 绑定目标并立即开始记录
     * （回响之箭触发）
     */
    public static void bindTargetAndStart(ServerPlayer player, LivingEntity target) {
        UUID uuid = player.getUUID();
        TimeData data = PLAYER_TIME_DATA.computeIfAbsent(uuid, k -> new TimeData());

        data.boundTarget = target.getUUID();
        data.positions.clear();
        data.isRecording = true;
        data.isRewinding = false;
        data.skipNext = false;

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
package com.hasoook.hasoook.event.block;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.block.custom.BuildingBlockBlock;
import com.hasoook.hasoook.block.custom.BuildingBlockCarpetBlock;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.damage.ModDamageSources;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 积木踩踏 / 摔落 / 鞋子附着 / 一级伤残效果。
 */
public class BuildingBlockStepEvent {

    private static final int WALK_COOLDOWN = 10;
    private static final int LAND_COOLDOWN = 30;
    // 一级伤残配置值在下面 applyDisabilityPose 块中通过 Config 读取
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> LAST_BLOCK_HURT = new HashMap<>();
    private static final Map<UUID, Long> LAST_WALK_HURT = new HashMap<>();
    private static final Map<UUID, Long> LAST_LAND_HURT = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();
    private static final Map<UUID, Double> AIR_START_Y = new HashMap<>();

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!entity.isAlive()) {
            DISABLED_POSE.remove(entity.getUUID());
            return;
        }

        // 一级伤残效果：躺下 + 减速 + 效果结束时恢复
        applyDisabilityPose(living);

        // 击飞轨迹粒子 + 落地烟雾
        if (level instanceof ServerLevel serverLevel) {
            UUID uuid = entity.getUUID();
            Integer trailTicks = DISABILITY_TRAIL.get(uuid);
            boolean onGround = entity.onGround();
            Boolean wasInAir = DISABILITY_WAS_IN_AIR.get(uuid);

            // 轨迹粒子：剩余 tick > 0 且在空中
            if (trailTicks != null && trailTicks > 0 && !onGround) {
                DISABILITY_TRAIL.put(uuid, trailTicks - 1);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                        2, 0.2, 0.2, 0.2, 0.02);
            }

            // 落地检测与轨迹是否过期无关
            if (wasInAir != null && wasInAir && onGround) {
                serverLevel.sendParticles(ParticleTypes.POOF,
                        entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        20, 0.5, 0.1, 0.5, 0.05);

                serverLevel.sendParticles(ParticleTypes.DUST_PLUME,
                        entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        20, 0.5, 0.1, 0.5, 0.05);

                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        5, 0.5, 0.1, 0.5, 0.05);

                DISABILITY_WAS_IN_AIR.put(uuid, false);
                DISABILITY_TRAIL.remove(uuid);
            }

            // 更新空中状态
            if (trailTicks != null) {
                DISABILITY_WAS_IN_AIR.put(uuid, !onGround);
            }
            if (trailTicks != null && trailTicks <= 1) {
                DISABILITY_TRAIL.remove(uuid);
            }
        }

        long now = level.getGameTime();

        ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
        int attachedBlocks = boots.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);

        // ═══════════════════════════════════════════
        // 鞋子塞了积木（致残躺倒时跳过）
        // ═══════════════════════════════════════════
        if (attachedBlocks > 0 && !living.hasEffect(ModEffects.DISABILITY)) {
            boolean onGround = entity.onGround();
            Boolean wasOnGround = WAS_ON_GROUND.put(entity.getUUID(), onGround);

            if (onGround) {
                BlockPos current = entity.blockPosition();
                BlockPos prev = LAST_POS.put(entity.getUUID(), current);
                boolean moved = prev != null && !current.equals(prev);

                if (moved) {
                    Long last = LAST_WALK_HURT.get(entity.getUUID());
                    if (last == null || now - last >= WALK_COOLDOWN) {
                        float dm = getDamagePerBlock(level) * attachedBlocks;
                        applyDamageOrDisability(living, level, dm);
                        tryBreakBootBlock(living, boots);
                        LAST_WALK_HURT.put(entity.getUUID(), now);
                    }
                }
            }

            if (wasOnGround != null && !wasOnGround && onGround) {
                Long last = LAST_LAND_HURT.get(entity.getUUID());
                if (last == null || now - last >= LAND_COOLDOWN) {
                    Double startY = AIR_START_Y.remove(entity.getUUID());
                    float fallDist = startY != null ? (float)(startY - entity.getY()) : 0;
                    if (fallDist > 0.5F) {
                        float dm = getDamagePerBlock(level) * attachedBlocks * (1.0F + fallDist);
                        applyDamageOrDisability(living, level, dm);
                        tryBreakBootBlock(living, boots);
                        LAST_LAND_HURT.put(entity.getUUID(), now);
                    }
                }
            }

            if (wasOnGround != null && wasOnGround && !onGround) {
                AIR_START_Y.put(entity.getUUID(), entity.getY());
            }

            return;
        }

        LAST_POS.remove(entity.getUUID());
        LAST_WALK_HURT.remove(entity.getUUID());
        LAST_LAND_HURT.remove(entity.getUUID());
        WAS_ON_GROUND.remove(entity.getUUID());
        AIR_START_Y.remove(entity.getUUID());

        // ═══════════════════════════════════════════
        // 脚下积木/床检测
        // ═══════════════════════════════════════════
        BlockPos pos = entity.blockPosition();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        int blockCount;
        if (block instanceof BuildingBlockCarpetBlock) {
            blockCount = state.getValue(BuildingBlockCarpetBlock.BLOCKS);
        } else if (block instanceof BuildingBlockBlock) {
            blockCount = 1;
        } else if (block instanceof BedBlock) {
            blockCount = getBedBlockCount(pos);
            if (blockCount <= 0) return;
        } else {
            return;
        }

        if (!boots.isEmpty()) return;

        Long last = LAST_BLOCK_HURT.get(entity.getUUID());
        if (last != null && now - last < WALK_COOLDOWN) return;

        float dm = getDamagePerBlock(level) * blockCount;
        applyDamageOrDisability(living, level, dm);
        LAST_BLOCK_HURT.put(entity.getUUID(), now);

        // 积木伤害后有概率破碎
        if (RANDOM.nextFloat() < Config.BUILDING_BLOCK_BREAK_CHANCE.get().floatValue()) {
            if (block instanceof BuildingBlockBlock) {
                level.destroyBlock(pos, false);
            } else if (block instanceof BuildingBlockCarpetBlock) {
                int count = state.getValue(BuildingBlockCarpetBlock.BLOCKS);
                if (count <= 1) {
                    Block vanillaCarpet = BuildingBlockCarpetBlock.getVanillaCarpet(
                            state.getValue(BuildingBlockCarpetBlock.COLOR));
                    level.setBlock(pos, vanillaCarpet.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, state.setValue(BuildingBlockCarpetBlock.BLOCKS, count - 1), 3);
                }
                // 破碎音效 + 方块破碎粒子
                level.playSound(null, pos, state.getSoundType(level, pos, null).getBreakSound(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    var blockOption = new BlockParticleOption(ParticleTypes.BLOCK,
                            ModBlocks.BUILDING_BLOCK.get().defaultBlockState());
                    serverLevel.sendParticles(blockOption,
                            pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                            12, 0.2, 0.1, 0.2, 0.05);
                }
            } else if (block instanceof BedBlock) {
                int count = getBedBlockCount(pos);
                setBedBlockCount(pos, count - 1);
                // 破碎音效 + 方块破碎粒子
                level.playSound(null, pos, state.getSoundType(level, pos, null).getBreakSound(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    var blockOption = new BlockParticleOption(ParticleTypes.BLOCK,
                            ModBlocks.BUILDING_BLOCK.get().defaultBlockState());
                    serverLevel.sendParticles(blockOption,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            12, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    // 一级伤残效果：强制 SLEEPING + 冻结朝向 + 加大碰撞箱 + 效果结束时恢复
    // 减速由 DisabilityEffect 内置的属性修饰符实现。
    // ═══════════════════════════════════════════
    private static final Set<UUID> DISABLED_POSE = new HashSet<>();
    private static final Map<UUID, Double> DISABLED_Y_OFFSET = new HashMap<>();
    private static final Map<UUID, Float> DISABLED_FROZEN_ROT = new HashMap<>();
    private static final Map<UUID, Integer> DISABILITY_TRAIL = new HashMap<>();
    private static final Map<UUID, Boolean> DISABILITY_WAS_IN_AIR = new HashMap<>();
    /// 被致残击飞的实体，下一次摔落伤害调整为 0.1
    private static final Set<UUID> DISABILITY_KNOCKBACK_FALL = new HashSet<>();
    /// 床内藏匿的积木计数（Key: BlockPos）
    private static final Map<BlockPos, Integer> BED_BLOCKS = new HashMap<>();
    private static final double DISABILITY_LIFT = 0.15;

    // ── 床内积木存取（供 BuildingBlockItem 调用）──
    public static int getBedBlockCount(BlockPos pos) {
        return BED_BLOCKS.getOrDefault(pos, 0);
    }
    public static void setBedBlockCount(BlockPos pos, int count) {
        if (count <= 0) BED_BLOCKS.remove(pos);
        else BED_BLOCKS.put(pos, Math.min(count, 16));
    }

    private static void applyDisabilityPose(LivingEntity living) {
        UUID uuid = living.getUUID();

        if (!living.hasEffect(ModEffects.DISABILITY)) {
            // 恢复：先降 Y（小碰撞箱不会穿地），再扩碰撞箱站立，最后抬升防止卡地
            Double yOff = DISABLED_Y_OFFSET.remove(uuid);
            if (yOff != null) {
                living.setPos(living.getX(), living.getY() - yOff, living.getZ());
            }
            if (DISABLED_POSE.remove(uuid)) {
                double oldHeight = living.getBbHeight(); // 躺卧碰撞箱高度
                living.setPose(Pose.STANDING);
                living.refreshDimensions();
                double newHeight = living.getBbHeight(); // 站立碰撞箱高度
                double lift = (newHeight - oldHeight) / 2.0;
                living.setPos(living.getX(), living.getY() + lift, living.getZ());
            }
            DISABLED_FROZEN_ROT.remove(uuid);
            return;
        }

        // SLEEPING → 仅非玩家（玩家由客户端 onClientTick 处理）
        if (living.getType() != EntityType.PLAYER) {
            living.setPose(Pose.SLEEPING);
            living.refreshDimensions();

            living.walkAnimation.setSpeed(0);
            living.walkAnimation.position(0);
            DISABLED_POSE.add(uuid);

            // 冻结倒下瞬间的朝向
            if (!DISABLED_FROZEN_ROT.containsKey(uuid)) {
                DISABLED_FROZEN_ROT.put(uuid, living.yBodyRot);
            }
            float frozenRot = DISABLED_FROZEN_ROT.get(uuid);
            living.yBodyRot = frozenRot;
            living.yBodyRotO = frozenRot;
            living.yHeadRot = frozenRot;
            living.yHeadRotO = frozenRot;

            // 抬升身体
            if (!DISABLED_Y_OFFSET.containsKey(uuid)) {
                living.setPos(living.getX(), living.getY() + DISABILITY_LIFT, living.getZ());
                DISABLED_Y_OFFSET.put(uuid, DISABILITY_LIFT);
            }
        }

        // 禁止潜行（双端生效）
        if (living.isShiftKeyDown()) {
            living.setShiftKeyDown(false);
        }
    }

    /// 睡上藏了积木的床（满足所有睡觉条件时）→ 触发致残
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return;
        if (event.getItemStack().is(ModItems.BUILDING_BLOCK.get())) return;

        // 检查床头和床尾两个位置的积木
        Direction facing = state.getValue(BedBlock.FACING);
        boolean isHead = state.getValue(BedBlock.PART) == BedPart.HEAD;
        BlockPos otherPos = isHead ? pos.relative(facing.getOpposite()) : pos.relative(facing);
        int count = getBedBlockCount(pos) + getBedBlockCount(otherPos);
        if (count <= 0) return;

        // 检查是否满足睡觉条件（夜晚/雷暴）
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        long dayTime = serverLevel.getDayTime() % 24000;
        boolean isNight = dayTime > 12542 && dayTime < 23460;
        if (!isNight && !serverLevel.isThundering()) return;

        event.setCanceled(true);
        triggerDisability(event.getEntity(), event.getLevel());
    }

    /// 床被破坏时掉落藏匿的积木（床头床尾任意一端被挖都汇总掉落）
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof BedBlock)) return;
        BlockPos pos = event.getPos();

        // 计算另一半位置
        Direction facing = state.getValue(BedBlock.FACING);
        boolean isHead = state.getValue(BedBlock.PART) == BedPart.HEAD;
        BlockPos otherPos = isHead ? pos.relative(facing.getOpposite()) : pos.relative(facing);

        // 汇总两端积木
        int total = getBedBlockCount(pos) + getBedBlockCount(otherPos);
        if (total <= 0) return;

        BED_BLOCKS.remove(pos);
        BED_BLOCKS.remove(otherPos);

        Block.popResource((Level) event.getLevel(), pos,
                new ItemStack(ModItems.BUILDING_BLOCK.get(), total));
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        // 致残击飞摔落：伤害固定为 1（≈0.5 心，MC 最小正数伤害）
        if (DISABILITY_KNOCKBACK_FALL.remove(entity.getUUID())) {
            event.setDistance(4.0F);
        }

        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        int attachedBlocks = boots.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);

        // 致残躺倒时跳过鞋内积木的摔落伤害乘算
        if (attachedBlocks > 0 && !entity.hasEffect(ModEffects.DISABILITY)) {
            double fallDist = event.getDistance();
            event.setDamageMultiplier(event.getDamageMultiplier() * (1.0F + attachedBlocks) * (float)(1.0 + fallDist));
            return;
        }

        BlockPos pos = entity.blockPosition();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        int blockCount;
        if (block instanceof BuildingBlockCarpetBlock) {
            blockCount = state.getValue(BuildingBlockCarpetBlock.BLOCKS);
        } else if (block instanceof BuildingBlockBlock) {
            blockCount = 1;
        } else if (block instanceof BedBlock) {
            blockCount = getBedBlockCount(pos);
            if (blockCount <= 0) return;
        } else {
            return;
        }

        if (!boots.isEmpty()) {
            event.setCanceled(true);
            boots.hurtAndBreak(blockCount, entity, EquipmentSlot.FEET);
        } else {
            event.setDamageMultiplier(event.getDamageMultiplier() * (1.0F + blockCount));
        }
    }

    /// 一级伤残状态下吃附魔金苹果 → 立即恢复
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!living.hasEffect(ModEffects.DISABILITY)) return;
        if (!event.getItem().is(Items.ENCHANTED_GOLDEN_APPLE)) return;

        living.removeEffect(ModEffects.DISABILITY);
    }

    private static void applyDamageOrDisability(LivingEntity entity, Level level, float normalDamage) {
        // 创造/旁观/无敌实体/无伤害不触发
        if (entity.isInvulnerable()) return;
        if (entity instanceof net.minecraft.world.entity.player.Player player
                && (player.isCreative() || player.isSpectator())) return;
        if (normalDamage <= 0) return;

        // 伤害超过目标最大生命值一半 → 必定致残（但不附加致残伤害）
        if (normalDamage > entity.getMaxHealth() / 2.0F) {
            applyDamage(entity, level, normalDamage);
            triggerDisabilityEffect(entity, level);
            return;
        }

        if (RANDOM.nextFloat() < Config.DISABILITY_CHANCE.get().floatValue()) {
            triggerDisability(entity, level);
        } else {
            applyDamage(entity, level, normalDamage);
        }
    }

    /// 致残：伤害 + 特效 + 效果
    private static void triggerDisability(LivingEntity entity, Level level) {
        float damage = entity.getMaxHealth() * Config.DISABILITY_DAMAGE_RATIO.get().floatValue();
        applyDamage(entity, level, damage);
        triggerDisabilityEffect(entity, level);
    }

    /// 仅致残的特效与效果，不造成伤害（用于高伤害必定致残路径）
    private static void triggerDisabilityEffect(LivingEntity entity, Level level) {
        // 击飞：水平随机方向 + 垂直向上
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        double dx = Math.cos(angle) * 0.5;
        double dy = 1.0 + RANDOM.nextDouble() * 0.2; // 1.0~1.2 垂直击飞
        double dz = Math.sin(angle) * 0.5;
        entity.setDeltaMovement(entity.getDeltaMovement().add(dx, dy, dz));
        entity.hurtMarked = true;

        // 暴击粒子爆炸
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                    20, 0.4, 0.3, 0.4, 0.1);
        }
        // 飞行轨迹粒子（持续 20 tick = 1 秒）
        DISABILITY_TRAIL.put(entity.getUUID(), 20);
        // 标记：下次摔落伤害调整为 0.1
        DISABILITY_KNOCKBACK_FALL.add(entity.getUUID());

        entity.addEffect(new MobEffectInstance(ModEffects.DISABILITY,
                Config.DISABILITY_DURATION.get(), 0, false, false));
    }

    /// 鞋内积木概率破碎（与地面积木共用 breakChance 配置）
    private static void tryBreakBootBlock(LivingEntity entity, ItemStack boots) {
        if (RANDOM.nextFloat() < Config.BUILDING_BLOCK_BREAK_CHANCE.get().floatValue()) {
            int current = boots.getOrDefault(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), 0);
            if (current <= 1) {
                boots.remove(ModDataComponents.BUILDING_BLOCK_ATTACHED.get());
            } else {
                boots.set(ModDataComponents.BUILDING_BLOCK_ATTACHED.get(), current - 1);
            }
            // 破碎音效 + 方块破碎粒子
            Level level = entity.level();
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundType.STONE.getBreakSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (level instanceof ServerLevel serverLevel) {
                var blockOption = new BlockParticleOption(ParticleTypes.BLOCK,
                        ModBlocks.BUILDING_BLOCK.get().defaultBlockState());
                serverLevel.sendParticles(blockOption,
                        entity.getX(), entity.getY() + 0.1, entity.getZ(),
                        8, 0.2, 0.1, 0.2, 0.05);
            }
        }
    }

    private static float getDamagePerBlock(Level level) {
        return switch (level.getDifficulty()) {
            case PEACEFUL -> 0.0F;
            case EASY -> 0.5F;
            case NORMAL -> 1.0F;
            case HARD -> 2.0F;
        };
    }

    private static void applyDamage(LivingEntity target, Level level, float damage) {
        if (damage <= 0) return;
        if (level instanceof ServerLevel serverLevel) {
            target.hurt(ModDamageSources.buildingBlock(serverLevel), damage);
        } else {
            target.hurt(level.damageSources().generic(), damage);
        }
    }
}

package com.hasoook.hasoook.event.entity;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemState;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class CopperGolemBattleEvent {

    // ── Phase trackers ──
    // States: IDLE → MOVING_TO_CHEST → SEARCHING_CHEST → HAS_TNT → THROWING
    private static final Map<UUID, Integer> PHASE_TIMERS = new HashMap<>();
    private static final Map<UUID, BattlePhase> PHASES = new HashMap<>();
    // Track the chest position the golem is heading to
    private static final Map<UUID, BlockPos> TARGET_CHESTS = new HashMap<>();

    private static final Map<UUID, Integer> SCAN_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> THROW_COOLDOWNS = new HashMap<>();
    // Animation override: persist state across ticks against vanilla Brain
    private static final Map<UUID, Integer> ANIM_TIMERS = new HashMap<>();
    private static final Map<UUID, CopperGolemState> ANIM_STATES = new HashMap<>();

    private static final int SCAN_INTERVAL = 20;
    private static final int THROW_INTERVAL = 60;
    private static final int CHEST_SCAN_RADIUS = 16;  // 箱子扫描范围
    private static final int DETECT_RADIUS = 24;       // 索敌范围（大）
    private static final int ATTACK_RADIUS_SQ = 256;   // 攻击范围 16²（小于索敌范围）
    private static final double CHEST_REACH_DIST = 2.0; // 必须贴近箱子才能翻找
    // Animation durations
    private static final int SEARCH_CHEST_TICKS = 30;
    private static final int HOLD_TNT_DELAY = 10;
    private static final int ANIM_THROW_TICKS = 15;

    private enum BattlePhase {
        IDLE,              // 无事可做
        MOVING_TO_CHEST,   // 向铜箱子移动中
        SEARCHING_CHEST,   // 翻找箱子中（播放GETTING_ITEM动画）
        HOLDING_TNT,       // 刚拿到TNT，短暂停顿
        THROWING           // 投掷TNT中（播放DROPPING_ITEM动画）
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        var entity = event.getEntity();

        if (!(entity instanceof CopperGolem golem)) {
            return;
        }

        Level level = golem.level();
        if (level.isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        UUID uuid = golem.getUUID();
        boolean battleMode = golem.getData(ModAttachments.COPPER_GOLEM_BATTLE_MODE);

        if (!battleMode) {
            SCAN_COOLDOWNS.remove(uuid);
            THROW_COOLDOWNS.remove(uuid);
            ANIM_TIMERS.remove(uuid);
            ANIM_STATES.remove(uuid);
            PHASE_TIMERS.remove(uuid);
            PHASES.remove(uuid);
            TARGET_CHESTS.remove(uuid);
            return;
        }

        // ── Animation override every tick (fights vanilla Brain's IDLE reset) ──
        Integer animTimer = ANIM_TIMERS.get(uuid);
        CopperGolemState animState = ANIM_STATES.get(uuid);
        if (animTimer != null && animState != null && animTimer > 0) {
            golem.setState(animState);
            int remaining = animTimer - 1;
            if (remaining <= 0) {
                golem.setState(CopperGolemState.IDLE);
                ANIM_TIMERS.remove(uuid);
                ANIM_STATES.remove(uuid);
            } else {
                ANIM_TIMERS.put(uuid, remaining);
            }
        }

        BattlePhase phase = PHASES.getOrDefault(uuid, BattlePhase.IDLE);
        boolean hasTnt = golem.getMainHandItem().is(Items.TNT);

        // ═══════════════════════════════════════════
        //  PHASE: IDLE — 决定下一步做什么
        // ═══════════════════════════════════════════
        if (phase == BattlePhase.IDLE) {
            if (hasTnt) {
                // 手上有TNT → 索敌范围大，但只在攻击范围内投掷
                LivingEntity target = findNearestHostile(level, golem);
                if (target != null && target.isAlive()) {
                    double distSq = golem.distanceToSqr(target);
                    if (distSq <= ATTACK_RADIUS_SQ) {
                        // 在攻击范围内 → 投掷
                        golem.getLookControl().setLookAt(target);
                        throwTntHighArc(serverLevel, golem, target);
                        golem.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        THROW_COOLDOWNS.put(uuid, THROW_INTERVAL);
                        golem.setState(CopperGolemState.DROPPING_ITEM);
                        ANIM_TIMERS.put(uuid, ANIM_THROW_TICKS);
                        ANIM_STATES.put(uuid, CopperGolemState.DROPPING_ITEM);
                        golem.swing(InteractionHand.MAIN_HAND);
                    } else {
                        // 在索敌范围内但太远 → 靠近目标
                        golem.getLookControl().setLookAt(target);
                        golem.getNavigation().moveTo(target, 1.0);
                    }
                }
            } else {
                // 手上没TNT → 扫描箱子
                int scanCd = SCAN_COOLDOWNS.getOrDefault(uuid, 0);
                if (scanCd > 0) {
                    SCAN_COOLDOWNS.put(uuid, scanCd - 1);
                    return;
                }
                SCAN_COOLDOWNS.put(uuid, SCAN_INTERVAL);

                BlockPos chestPos = findNearestCopperChestWithTnt(level, golem);
                if (chestPos != null) {
                    TARGET_CHESTS.put(uuid, chestPos);
                    PHASES.put(uuid, BattlePhase.MOVING_TO_CHEST);
                } else if (golem.tickCount % 20 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            golem.getX(), golem.getY() + golem.getEyeHeight(), golem.getZ(),
                            3, 0.2, 0.1, 0.2, 0.01);
                }
            }
            return;
        }

        // ═══════════════════════════════════════════
        //  PHASE: MOVING_TO_CHEST
        // ═══════════════════════════════════════════
        if (phase == BattlePhase.MOVING_TO_CHEST) {
            BlockPos chestPos = TARGET_CHESTS.get(uuid);
            if (chestPos == null) {
                PHASES.put(uuid, BattlePhase.IDLE);
                return;
            }

            // Verify chest still has TNT
            BlockEntity be = level.getBlockEntity(chestPos);
            if (!(be instanceof ChestBlockEntity chestBE) || !hasTnt(chestBE)) {
                // Chest empty or destroyed — rescan
                TARGET_CHESTS.remove(uuid);
                PHASES.put(uuid, BattlePhase.IDLE);
                return;
            }

            // Navigate toward chest
            Vec3 chestCenter = Vec3.atCenterOf(chestPos);
            golem.getNavigation().moveTo(chestCenter.x, chestPos.getY(), chestCenter.z, 1.0);
            golem.getLookControl().setLookAt(chestCenter.x, chestCenter.y, chestCenter.z);

            // Must be right next to the chest to start searching
            if (golem.position().distanceTo(chestCenter) <= CHEST_REACH_DIST) {
                golem.getNavigation().stop();
                PHASES.put(uuid, BattlePhase.SEARCHING_CHEST);
                PHASE_TIMERS.put(uuid, SEARCH_CHEST_TICKS);
                // 开始翻找动画
                golem.setState(CopperGolemState.GETTING_ITEM);
                ANIM_TIMERS.put(uuid, SEARCH_CHEST_TICKS);
                ANIM_STATES.put(uuid, CopperGolemState.GETTING_ITEM);
                golem.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }

        // ═══════════════════════════════════════════
        //  PHASE: SEARCHING_CHEST — 翻箱子中
        // ═══════════════════════════════════════════
        if (phase == BattlePhase.SEARCHING_CHEST) {
            int timer = PHASE_TIMERS.getOrDefault(uuid, 0) - 1;
            PHASE_TIMERS.put(uuid, timer);

            BlockPos chestPos = TARGET_CHESTS.get(uuid);
            if (chestPos != null) {
                Vec3 chestCenter = Vec3.atCenterOf(chestPos);
                golem.getLookControl().setLookAt(chestCenter.x, chestCenter.y, chestCenter.z);
            }

            // 翻找过程中播放音效和粒子
            if (timer > SEARCH_CHEST_TICKS * 0.6 && timer % 5 == 0) {
                if (chestPos != null) {
                    serverLevel.playSound(null, chestPos,
                            SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.3F, 1.4F);
                }
            }

            if (timer <= 0) {
                // 翻找完成 → 取TNT
                BlockEntity be = chestPos != null ? level.getBlockEntity(chestPos) : null;
                boolean taken = false;
                if (be instanceof ChestBlockEntity chestBE) {
                    taken = removeOneTnt(chestBE);
                }

                if (taken) {
                    golem.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TNT));
                    serverLevel.playSound(null, golem,
                            SoundEvents.COPPER_HIT, SoundSource.NEUTRAL, 0.8F, 1.5F);
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            golem.getX(), golem.getY() + golem.getEyeHeight(), golem.getZ(),
                            8, 0.3, 0.3, 0.3, 0.05);
                    // 拿到TNT后短暂停顿再扔
                    PHASES.put(uuid, BattlePhase.HOLDING_TNT);
                    PHASE_TIMERS.put(uuid, HOLD_TNT_DELAY);
                } else {
                    // 箱子空了
                    golem.setState(CopperGolemState.GETTING_NO_ITEM);
                    ANIM_TIMERS.put(uuid, 10);
                    ANIM_STATES.put(uuid, CopperGolemState.GETTING_NO_ITEM);
                    PHASES.put(uuid, BattlePhase.IDLE);
                }

                TARGET_CHESTS.remove(uuid);
            }
            return;
        }

        // ═══════════════════════════════════════════
        //  PHASE: HOLDING_TNT — 拿到TNT后短暂停顿
        // ═══════════════════════════════════════════
        if (phase == BattlePhase.HOLDING_TNT) {
            int timer = PHASE_TIMERS.getOrDefault(uuid, 0) - 1;
            PHASE_TIMERS.put(uuid, timer);
            if (timer <= 0) {
                PHASES.put(uuid, BattlePhase.IDLE);
            }
            return;
        }
    }

    // ═══════════════════════════════════════════
    //  高抛物线投掷 TNT（精准西瓜投手风格）
    // ═══════════════════════════════════════════
    private static void throwTntHighArc(ServerLevel level, CopperGolem golem, LivingEntity target) {
        Vec3 golemPos = golem.position().add(0, golem.getEyeHeight(), 0);
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 diff = targetPos.subtract(golemPos);
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double heightDiff = diff.y; // positive if target is above

        PrimedTnt tnt = new PrimedTnt(level,
                golemPos.x, golemPos.y + 0.3, golemPos.z, golem);
        tnt.setFuse(40);

        // Minecraft gravity: velocity.y -= 0.04 each tick → acceleration = 0.04/tick
        // Equation: y(t) = vy*t - 0.02*t²
        // To land at same height at t: vy = 0.02 * t
        // Add arc boost for visual effect: extra vy to peak ~3 blocks above launch
        double g = 0.04;
        double flightTicks;
        double horizontalSpeed;
        double verticalSpeed;

        if (horizontalDist < 1.5) {
            // Very close — almost straight up, low horizontal
            horizontalSpeed = 0.15;
            verticalSpeed = 0.9;
        } else {
            // Flight time proportional to distance (slightly less to avoid floating feel)
            flightTicks = Math.min(28, horizontalDist * 1.1 + 6);
            // Speed needed to exactly cover distance in flightTicks
            horizontalSpeed = horizontalDist / flightTicks;
            // vy = heightDiff/t + g*t/2 + arcBoost  (heightDiff>0 when target above)
            verticalSpeed = heightDiff / flightTicks + g * flightTicks / 2.0 + 0.2;
        }

        // Normalize horizontal direction (no random spread for accuracy)
        if (horizontalDist > 0.01) {
            double dirX = diff.x / horizontalDist;
            double dirZ = diff.z / horizontalDist;
            tnt.setDeltaMovement(dirX * horizontalSpeed, verticalSpeed, dirZ * horizontalSpeed);
        } else {
            tnt.setDeltaMovement(0, verticalSpeed, 0);
        }

        level.addFreshEntity(tnt);

        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME,
                golem.getX(), golem.getY() + golem.getEyeHeight(), golem.getZ(),
                5, 0.15, 0.15, 0.15, 0.05);
        level.sendParticles(ParticleTypes.SMOKE,
                golem.getX(), golem.getY() + golem.getEyeHeight(), golem.getZ(),
                8, 0.2, 0.2, 0.2, 0.08);
    }

    // ═══════════════════════════════════════════
    //  查找最近的敌对生物
    // ═══════════════════════════════════════════
    private static LivingEntity findNearestHostile(Level level, CopperGolem golem) {
        AABB area = golem.getBoundingBox().inflate(DETECT_RADIUS);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> {
            if (e == golem || !e.isAlive()) return false;
            if (e instanceof Monster) return true;
            if (e instanceof Mob mob && mob.getTarget() == golem) return true;
            return false;
        })) {
            double dist = golem.distanceToSqr(entity);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    // ═══════════════════════════════════════════
    //  查找最近的含TNT的原版铜箱子
    // ═══════════════════════════════════════════
    private static BlockPos findNearestCopperChestWithTnt(Level level, CopperGolem golem) {
        BlockPos golemPos = golem.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -CHEST_SCAN_RADIUS; x <= CHEST_SCAN_RADIUS; x++) {
            for (int y = -CHEST_SCAN_RADIUS; y <= CHEST_SCAN_RADIUS; y++) {
                for (int z = -CHEST_SCAN_RADIUS; z <= CHEST_SCAN_RADIUS; z++) {
                    mutablePos.set(golemPos.getX() + x, golemPos.getY() + y, golemPos.getZ() + z);
                    if (level.getBlockState(mutablePos).getBlock()
                            instanceof net.minecraft.world.level.block.CopperChestBlock) {
                        BlockEntity be = level.getBlockEntity(mutablePos);
                        if (be instanceof ChestBlockEntity chestBE && hasTnt(chestBE)) {
                            double dist = golem.distanceToSqr(Vec3.atCenterOf(mutablePos));
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearest = mutablePos.immutable();
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean hasTnt(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(Items.TNT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeOneTnt(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(Items.TNT)) {
                stack.shrink(1);
                chest.setChanged();
                return true;
            }
        }
        return false;
    }
}

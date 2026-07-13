package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.custom.SocksItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class SocksEvent {

    /// 服务端累加器：记录玩家当前穿着袜子时的累积磨损值（未写入物品的）
    private static final Map<UUID, Integer> ACCUMULATOR = new HashMap<>();
    /// 粒子计时器
    private static final Map<UUID, Integer> PARTICLE_TIMER = new HashMap<>();
    /// 上一 tick 是否在地面（用于跳跃检测）
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();
    /// 植被破坏计时器（每 N tick 处理一次）
    private static final Map<UUID, Integer> BLOCK_DESTROY_TIMER = new HashMap<>();
    /// 生物逃离寻路冷却（UUID → 上次寻路的 player.tickCount）
    private static final Map<UUID, Integer> LAST_FLEE_TICK = new HashMap<>();

    // 磨损速率
    private static final int WEAR_WALK = 1;      // 走路：每 tick +1
    private static final int WEAR_SPRINT = 2;    // 疾跑：每 tick +2
    private static final int WEAR_JUMP = 10;     // 跳跃：每次 +10

    /**
     * 中毒粒子颜色 — 等级越高越深
     */
    private static final int[] POISON_COLORS = {
            0x000000,       // stage 0 不使用
            0xFF7BA05B,     // stage 1 浅毒绿
            0xFF5A8A3C,     // stage 2
            0xFF3D6B22,     // stage 3
            0xFF254D0F,     // stage 4
            0xFF123004,     // stage 5
            0xFF061801,     // stage 6 几乎纯黑
    };

    /** 每个阶段的粒子间隔（tick） */
    private static final int[] PARTICLE_INTERVAL = {
            999, // stage 0 不使用
            20,  // stage 1 — 每秒
            12,  // stage 2
            8,   // stage 3
            5,   // stage 4
            3,   // stage 5
            2,   // stage 6 — 每2tick
    };

    /** 每个阶段每次生成的粒子数量 */
    private static final int[] PARTICLE_COUNT = {
            0,  // stage 0 不使用
            1,  // stage 1
            2,  // stage 2
            4,  // stage 3
            8,  // stage 4
            14, // stage 5
            22, // stage 6 — 浓烟滚滚
    };

    // ═══════════════════════════════════════════
    //  恶臭光环参数 — 逐级递增，内圈 < 外圈
    // ═══════════════════════════════════════════

    /** 驱离范围（stage 3+），越远越能闻到 */
    private static final double[] FLEE_RANGE = {
            0,  // stage 0
            0,  // stage 1
            0,  // stage 2
            5,  // stage 3 — 熏人鼻腔
            8,  // stage 4 — 令人窒息
            12, // stage 5 — 生化武器
            16, // stage 6 — 不可名状
    };

    /** 逃离寻路冷却（tick），避免每 tick 重新寻路导致抽搐 */
    private static final int FLEE_PATH_INTERVAL = 15;
    /** 逃离时移动速度倍率 */
    private static final double FLEE_SPEED = 1.2;
    /** 中毒持续时间（tick），足够长以覆盖逃离过程 */
    private static final int POISON_DURATION = 200; // 10 秒
    /** 逃离地图清理间隔（tick），防止内存泄漏 */
    private static final int FLEE_MAP_CLEAN_INTERVAL = 200;
    /// 逃离地图清理计时器
    private static int fleeCleanTimer = 0;

    /** 中毒范围（stage 4+），必须比同级驱离范围小 */
    private static final double[] POISON_RANGE = {
            0, 0, 0, 0,
            3,  // stage 4
            5,  // stage 5
            8,  // stage 6
    };

    /** 中毒等级（amplifier: 0=I, 1=II, 2=III） */
    private static final int[] POISON_AMPLIFIER = {
            0, 0, 0, 0,
            0,  // stage 4 — 中毒 I
            1,  // stage 5 — 中毒 II
            2,  // stage 6 — 中毒 III
    };

    /** 植被破坏最大范围（stage 5+），距离衰减 */
    private static final double[] DESTROY_RANGE = {
            0, 0, 0, 0, 0,
            6,  // stage 5
            10, // stage 6
    };

    /** 植被破坏间隔（tick） */
    private static final int BLOCK_DESTROY_INTERVAL = 6;
    /** 每次破坏尝试的随机方块数 */
    private static final int BLOCK_DESTROY_ATTEMPTS = 10;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();

        if (player.level().isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        UUID uuid = player.getUUID();

        // ── 获取脚部装备 ──
        ItemStack feetStack = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(feetStack.getItem() instanceof SocksItem)) {
            ACCUMULATOR.remove(uuid);
            PARTICLE_TIMER.remove(uuid);
            WAS_ON_GROUND.remove(uuid);
            BLOCK_DESTROY_TIMER.remove(uuid);
            LAST_FLEE_TICK.remove(uuid);
            return;
        }

        int baseWear = feetStack.getOrDefault(ModDataComponents.SOCKS_WEAR.get(), 0);
        int pending = ACCUMULATOR.getOrDefault(uuid, 0);
        int currentWear = baseWear + pending;
        int currentStage = SocksItem.getStage(currentWear);

        // ── 持续细流粒子 ──
        if (currentStage > 0) {
            int timer = PARTICLE_TIMER.getOrDefault(uuid, 0) + 1;
            int interval = PARTICLE_INTERVAL[currentStage];
            if (timer >= interval) {
                int count = PARTICLE_COUNT[currentStage];
                level.sendParticles(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, POISON_COLORS[currentStage]),
                        player.getX(), player.getY() + 0.25, player.getZ(),
                        count, 0.35, 0.25, 0.35, 0.02);
                timer = 0;
            }
            PARTICLE_TIMER.put(uuid, timer);
        }

        // ══════════════════════════════════════
        //  恶臭光环 — 每 tick 都会执行
        // ══════════════════════════════════════

        // Stage 3+: 驱离生物（范围最大）
        if (currentStage >= 3) {
            applyFleeAura(level, player, currentStage);
        }

        // Stage 4+: 中毒光环（范围中等）
        if (currentStage >= 4) {
            applyPoisonAura(level, player, currentStage);
        }

        // Stage 5+: 破坏植被（范围最小，间歇执行）
        if (currentStage >= 5) {
            applyBlockDestructionAura(level, player, uuid, currentStage);
        }

        // Stage 5+: 穿戴者也受影响（比对其他生物低一级）
        if (currentStage >= 5) {
            int selfPoisonAmp = POISON_AMPLIFIER[currentStage] - 1; // 比光环低一级
            MobEffectInstance existing = player.getEffect(MobEffects.POISON);
            if (existing == null || existing.getAmplifier() < selfPoisonAmp
                    || (existing.getAmplifier() == selfPoisonAmp && existing.getDuration() <= 60)) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, selfPoisonAmp,
                        false, true));
            }
            // stage 6 额外反胃 I
            if (currentStage >= 6) {
                MobEffectInstance nausea = player.getEffect(MobEffects.NAUSEA);
                if (nausea == null || nausea.getDuration() <= 60) {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0,
                            false, false));
                }
            }
        }

        // ── 磨损累积 ──
        boolean onGround = player.onGround();
        boolean wasOnGrnd = WAS_ON_GROUND.getOrDefault(uuid, true);
        int addWear = 0;

        // 跳跃检测：从地面进入空中 + 有向上的速度
        if (wasOnGrnd && !onGround && player.getDeltaMovement().y > 0) {
            addWear += WEAR_JUMP;
        }

        // 地面移动
        if (onGround && player.getDeltaMovement().horizontalDistanceSqr() > 0.0001) {
            addWear += player.isSprinting() ? WEAR_SPRINT : WEAR_WALK;
        }

        WAS_ON_GROUND.put(uuid, onGround);

        if (addWear == 0) {
            return;
        }

        int oldStage = SocksItem.getStage(baseWear + pending);
        pending += addWear;
        int newWear = baseWear + pending;
        int newStage = SocksItem.getStage(newWear);

        if (oldStage != newStage) {
            feetStack.set(ModDataComponents.SOCKS_WEAR.get(), newWear);
            ACCUMULATOR.put(uuid, 0);
        } else {
            ACCUMULATOR.put(uuid, pending);
        }
    }

    // ══════════════════════════════════════
    //  光环实现方法
    // ══════════════════════════════════════

    /**
     * Stage 3+：驱离范围内的生物。
     * 使用寻路系统让生物主动向远离玩家的位置逃跑，
     * 每隔一段时间重新计算路径，配合加速移动制造"夺路而逃"的效果。
     */
    private static void applyFleeAura(ServerLevel level, Player player, int stage) {
        double range = FLEE_RANGE[stage];
        AABB box = player.getBoundingBox().inflate(range);
        int now = player.tickCount;

        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            UUID mobId = mob.getUUID();

            // 每 tick 都清除仇恨，防止它一边跑一边想回来
            mob.setTarget(null);

            // 只在冷却结束后重新计算逃离路径（避免每 tick 寻路导致抽搐）
            int lastFlee = LAST_FLEE_TICK.getOrDefault(mobId, -FLEE_PATH_INTERVAL);
            if (now - lastFlee < FLEE_PATH_INTERVAL) {
                continue;
            }
            LAST_FLEE_TICK.put(mobId, now);

            // ── 计算逃离目标点 ──
            Vec3 mobPos = mob.position();
            Vec3 playerPos = player.position();
            double dist = mobPos.distanceTo(playerPos);

            // 方向：从玩家指向生物（远离方向）
            Vec3 awayDir;
            if (dist < 0.01) {
                // 太近了，随机选个方向
                awayDir = new Vec3(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5).normalize();
            } else {
                awayDir = mobPos.subtract(playerPos).normalize();
            }

            // 目标距离：当前距离 + (范围 - 当前距离) * 1.5，至少跑出范围外 3 格
            double fleeDist = Math.max(range - dist + 3, 3);
            Vec3 fleeTarget = mobPos.add(awayDir.x * fleeDist, 0, awayDir.z * fleeDist);

            // 找地面高度，防止导航到空中或地下
            BlockPos groundPos = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    BlockPos.containing(fleeTarget)
            );
            fleeTarget = new Vec3(fleeTarget.x, groundPos.getY(), fleeTarget.z);

            // 让生物寻路逃跑，速度稍快制造惊慌感
            mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, FLEE_SPEED);
        }

        // ── 定期清理已离开范围或死亡的生物记录，防止内存泄漏 ──
        fleeCleanTimer++;
        if (fleeCleanTimer >= FLEE_MAP_CLEAN_INTERVAL) {
            fleeCleanTimer = 0;
            int threshold = now - FLEE_MAP_CLEAN_INTERVAL * 2;
            LAST_FLEE_TICK.values().removeIf(tick -> tick < threshold);
        }
    }

    /**
     * Stage 4+：对范围内的生物施加中毒效果。
     * 只在毒效快结束时才刷新，避免每 tick 覆盖导致毒伤计时器被重置。
     */
    private static void applyPoisonAura(ServerLevel level, Player player, int stage) {
        double range = POISON_RANGE[stage];
        int amplifier = POISON_AMPLIFIER[stage];
        AABB box = player.getBoundingBox().inflate(range);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == player) continue;

            MobEffectInstance existing = entity.getEffect(MobEffects.POISON);

            // 已有更强的中毒 → 不降级
            if (existing != null && existing.getAmplifier() > amplifier) continue;

            // 已有同级中毒且剩余时间充足 → 不刷新，让它自然触发毒伤
            if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 60) continue;

            // 没中毒 / 毒效快没了 / 升级 → 施加/刷新
            entity.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    POISON_DURATION,  // 10 秒
                    amplifier,
                    false,            // 不显示粒子
                    true              // 可见
            ));
        }
    }

    /**
     * Stage 5+：间歇性破坏周围的植被。
     * 范围较大，越靠近玩家破坏概率越高（线性衰减），远处慢慢枯萎。
     * 草方块 → 泥土，树苗 → 枯木，花草/树叶/农作物直接摧毁。
     */
    private static void applyBlockDestructionAura(ServerLevel level, Player player, UUID uuid, int stage) {
        int timer = BLOCK_DESTROY_TIMER.getOrDefault(uuid, 0) + 1;
        if (timer < BLOCK_DESTROY_INTERVAL) {
            BLOCK_DESTROY_TIMER.put(uuid, timer);
            return;
        }
        BLOCK_DESTROY_TIMER.put(uuid, 0);

        double range = DESTROY_RANGE[stage];

        for (int attempt = 0; attempt < BLOCK_DESTROY_ATTEMPTS; attempt++) {
            // 在圆柱范围内随机选一个位置
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = level.random.nextDouble() * range;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            // 垂直范围：上下各 range 格
            double dy = (level.random.nextDouble() - 0.5) * range * 2;

            BlockPos pos = BlockPos.containing(
                    player.getX() + dx,
                    player.getY() + dy,
                    player.getZ() + dz
            );

            double actualDist = player.position().distanceTo(pos.getCenter());
            if (actualDist > range) continue;

            // ── 距离衰减：越远概率越低 ──
            double chance = 1.0 - (actualDist / range);
            if (level.random.nextDouble() > chance) continue;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            // ── 草方块 / 菌丝 / 灰化土 → 泥土 ──
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                spawnDestructionParticles(level, pos);
                continue;
            }

            // ── 树苗 / 杜鹃花丛 → 枯木 ──
            if (isSapling(state)) {
                level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
                spawnDestructionParticles(level, pos);
                continue;
            }

            // ── 花草 / 树叶 / 农作物 → 空气（不掉落）──
            if (isDestroyablePlant(state)) {
                level.destroyBlock(pos, false); // false = 不掉落物品
                spawnDestructionParticles(level, pos);
            }
        }
    }

    /** 判断是否为可被恶臭枯萎的树苗/杜鹃 */
    private static boolean isSapling(BlockState state) {
        return state.is(Blocks.OAK_SAPLING)
                || state.is(Blocks.SPRUCE_SAPLING)
                || state.is(Blocks.BIRCH_SAPLING)
                || state.is(Blocks.JUNGLE_SAPLING)
                || state.is(Blocks.ACACIA_SAPLING)
                || state.is(Blocks.DARK_OAK_SAPLING)
                || state.is(Blocks.CHERRY_SAPLING)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.AZALEA)
                || state.is(Blocks.FLOWERING_AZALEA);
    }

    /** 判断是否为可被恶臭摧毁的脆弱植物 */
    private static boolean isDestroyablePlant(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS)
                || state.is(Blocks.CRIMSON_ROOTS)
                || state.is(Blocks.WARPED_ROOTS)
                || state.is(Blocks.NETHER_SPROUTS)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.SMALL_DRIPLEAF)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.SPORE_BLOSSOM)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.WILDFLOWERS);
    }

    /** 破坏植被时的小粒子反馈 */
    private static void spawnDestructionParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                3, 0.2, 0.1, 0.2, 0.01
        );
    }

    /** 袜子糊脸计时器 + 中毒效果：每 tick 减 1，取最高臭味等级加中毒 */
    @SubscribeEvent
    public static void onSockFaceTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        String data = player.getData(ModAttachments.SOCK_FACE.get());
        if (data.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        int maxStage = 0;
        for (String part : data.split(",")) {
            int packed = Integer.parseInt(part);
            int remaining = (packed & 0xFFF) - 1; // 12 bits
            int wearStage = (packed >> 16) & 0xFF;
            if (wearStage > maxStage) maxStage = wearStage;
            if (remaining > 0) {
                int upper = packed & ~0xFFF;
                if (!sb.isEmpty()) sb.append(',');
                sb.append(upper | remaining);
            }
        }
        player.setData(ModAttachments.SOCK_FACE.get(), sb.toString());

        // 根据最高臭味等级施加中毒，完全模仿 applyPoisonAura 的逻辑
        if (maxStage >= 4) {
            int amplifier = maxStage - 4; // stage4→0(I), stage5→1(II), stage6→2(III)
            MobEffectInstance existing = player.getEffect(MobEffects.POISON);

            // 已有更强的中毒 → 不降级、不刷新
            boolean shouldSkip = (existing != null && existing.getAmplifier() > amplifier)
                    // 已有同级中毒且剩余时间充足 → 不刷新，让它自然触发毒伤
                    || (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 20);

            if (!shouldSkip) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amplifier,
                        false, true));
            }
        } else {
            // 没有 stage 4+ 的袜子 → 快速消除中毒（缩到 2 秒内）
            MobEffectInstance existing = player.getEffect(MobEffects.POISON);
            if (existing != null && existing.getDuration() > 40) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 30,
                        existing.getAmplifier(), false, true));
            }
        }

        // 脸上袜子反胃（stage 5+）
        if (maxStage >= 5) {
            int nauseaAmp = maxStage - 5; // stage5→0(I), stage6→1(II)
            MobEffectInstance existing = player.getEffect(MobEffects.NAUSEA);
            if (existing == null || existing.getDuration() <= 60) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, nauseaAmp,
                        false, false));
            }
        } else {
            MobEffectInstance existing = player.getEffect(MobEffects.NAUSEA);
            if (existing != null && existing.getDuration() > 40) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 30,
                        existing.getAmplifier(), false, false));
            }
        }
    }

}

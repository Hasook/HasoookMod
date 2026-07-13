package com.hasoook.hasoook.entity.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.entity.ModEntities;
import com.hasoook.hasoook.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

import org.jspecify.annotations.NonNull;

public class ArmorStandSwordProjectile extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> ID_RETURNING =
            SynchedEntityData.defineId(ArmorStandSwordProjectile.class, EntityDataSerializers.BOOLEAN);

    /** 忠诚附魔等级（0-3），0 表示没有忠诚 */
    private static final EntityDataAccessor<Byte> ID_LOYALTY =
            SynchedEntityData.defineId(ArmorStandSwordProjectile.class, EntityDataSerializers.BYTE);

    /** 同步完整物品堆（含数据组件），使客户端渲染器能读取装备 */
    private static final EntityDataAccessor<ItemStack> DATA_SWORD_STACK =
            SynchedEntityData.defineId(ArmorStandSwordProjectile.class, EntityDataSerializers.ITEM_STACK);

    private float damage = 8.0F;

    /** 是否已播放返回音效，防止每 tick 重复播放 */
    private boolean hasPlayedReturnSound = false;

    /** 是否应当触发忠诚返回（命中实体 / 停留方块中）*/
    private boolean shouldReturn = false;

    /** 冰霜行者附魔等级（从剑内存储的靴子读取），0 表示没有 */
    private int frostWalkerLevel = 0;

    /** 深海探索者附魔等级（从剑内存储的靴子读取），0 表示没有 */
    private int depthStriderLevel = 0;

    public ArmorStandSwordProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public ArmorStandSwordProjectile(LivingEntity shooter, Level level, ItemStack stack) {
        super(ModEntities.ARMOR_STAND_SWORD_PROJECTILE.get(), shooter, level, stack, null);
        // 把完整物品堆（含装备数据组件）写入同步字段，客户端渲染器需要它
        this.entityData.set(DATA_SWORD_STACK, stack.copy());

        // 读取剑上的忠诚附魔等级
        int loyalty = 0;
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null) {
            for (var entry : enchants.entrySet()) {
                if (entry.getKey().is(Enchantments.LOYALTY)) {
                    loyalty = entry.getValue();
                    break;
                }
            }
        }
        this.entityData.set(ID_LOYALTY, (byte) loyalty);

        // 读取剑内存储的靴子（槽位3=FEET）上的冰霜行者和深海探索者附魔等级
        ItemStack boots = getBootsFromSword(stack);
        this.frostWalkerLevel = ModEnchantmentHelper.getEnchantmentLevel(
                Enchantments.FROST_WALKER, boots);
        this.depthStriderLevel = ModEnchantmentHelper.getEnchantmentLevel(
                Enchantments.DEPTH_STRIDER, boots);

        if (shooter instanceof Player player && player.getAbilities().instabuild) {
            this.pickup = Pickup.CREATIVE_ONLY;
        } else {
            this.pickup = Pickup.ALLOWED;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_RETURNING, false);
        builder.define(ID_LOYALTY, (byte) 0);
        builder.define(DATA_SWORD_STACK, this.getDefaultPickupItem());
    }

    public boolean isReturning() {
        return this.entityData.get(ID_RETURNING);
    }

    public void setReturning(boolean returning) {
        this.entityData.set(ID_RETURNING, returning);
    }

    public byte getLoyaltyLevel() {
        return this.entityData.get(ID_LOYALTY);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected @NonNull ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.ARMOR_STAND_SWORD.get());
    }

    /**
     * 返回同步后的物品堆（含完整数据组件）。
     * 客户端渲染器依赖此方法读取装备信息。
     */
    public ItemStack getItem() {
        return this.entityData.get(DATA_SWORD_STACK);
    }

    @Override
    public void tick() {
        // 在方块中停留超过 4 tick 后，允许忠诚附魔触发返回
        if (this.inGroundTime > 4) {
            this.shouldReturn = true;
        }

        Entity owner = this.getOwner();
        int loyalty = this.entityData.get(ID_LOYALTY);

        // ═══════════════════════════════════════════════════════════════
        // 忠诚附魔返回逻辑（类似三叉戟）
        // ═══════════════════════════════════════════════════════════════
        if (loyalty > 0 && (this.shouldReturn || this.isNoPhysics()) && owner != null) {
            if (!isAcceptibleReturnOwner()) {
                // 主人已死亡或不合法 → 掉落物品
                if (!this.level().isClientSide() && this.pickup == Pickup.ALLOWED) {
                    this.spawnAtLocation((ServerLevel) this.level(), this.getPickupItem(), 0.1F);
                }
                this.discard();
                return;
            }

            // 标记为返回中（playerTouch 依赖此状态处理拾取）
            this.entityData.set(ID_RETURNING, true);
            this.setNoPhysics(true);

            Vec3 vec3 = owner.getEyePosition().subtract(this.position());
            this.setPos(this.getX(), this.getY() + vec3.y * 0.015 * loyalty, this.getZ());
            if (this.level().isClientSide()) {
                this.yOld = this.getY();
            }

            double speed = 0.05 * loyalty;
            this.setDeltaMovement(this.getDeltaMovement().scale(0.95).add(vec3.normalize().scale(speed)));

            // 播放返回音效（仅一次）
            if (!this.hasPlayedReturnSound) {
                this.playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 1.0F);
                this.hasPlayedReturnSound = true;
            }
        }
        // 主人死亡或不存在 → 掉落物品
        else if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide() && this.pickup == Pickup.ALLOWED) {
                this.spawnAtLocation((ServerLevel) this.level(), this.getPickupItem(), 0.1F);
                this.discard();
            }
            return;
        }

        // ═══════════════════════════════════════════════════════════════
        // 冰霜行者效果：飞行中仅在靠近水面时生成霜冰
        // ═══════════════════════════════════════════════════════════════
        if (!this.level().isClientSide() && this.frostWalkerLevel > 0 && !this.isReturning()
                && this.isNearWater((ServerLevel) this.level())) {
            this.applyFrostWalker((ServerLevel) this.level());
            // 每 20 tick（1秒）消耗靴子 1 点耐久，受耐久附魔减免
            if (this.tickCount % 20 == 0) {
                this.consumeBootsDurability();
            }
        }

        super.tick();
    }

    /**
     * 深海探索者效果：降低弹射物在水下的阻力，使飞行距离更远。
     * 默认水惯性为 0.6，每级深海探索者减少约 1/3 的水阻力。
     */
    @Override
    protected float getWaterInertia() {
        if (this.depthStriderLevel > 0) {
            return 0.6F + 0.4F * (this.depthStriderLevel / 3.0F);
        }
        return super.getWaterInertia();
    }

    /**
     * 从剑的存储槽位中读取靴子（槽位3）。
     */
    private static ItemStack getBootsFromSword(ItemStack swordStack) {
        ItemContainerContents icc = swordStack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(), ItemContainerContents.EMPTY);
        var stored = icc.stream().toList();
        if (stored.size() > 3) {
            return stored.get(3);
        }
        return ItemStack.EMPTY;
    }

    /**
     * 每 20 tick 调用一次，消耗剑内靴子 1 点耐久。
     * 受耐久（Unbreaking）附魔减免：等级越高越不容易消耗耐久。
     * 靴子损坏后自动从剑中移除，并停止冰霜行者效果。
     */
    private void consumeBootsDurability() {
        ItemStack swordStack = this.entityData.get(DATA_SWORD_STACK);
        ItemContainerContents icc = swordStack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(), ItemContainerContents.EMPTY);
        var stored = new ArrayList<>(icc.stream().toList());

        if (stored.size() <= 3) return;
        ItemStack boots = stored.get(3);
        if (boots.isEmpty() || !boots.isDamageableItem()) return;

        // 耐久附魔减免：等级 N → 1/(N+1) 概率消耗耐久
        int unbreakingLevel = ModEnchantmentHelper.getEnchantmentLevel(
                Enchantments.UNBREAKING, boots);
        if (unbreakingLevel > 0 && this.random.nextInt(unbreakingLevel + 1) != 0) {
            return; // 耐久附魔生效，本次不消耗
        }

        int newDamage = boots.getDamageValue() + 1;
        if (newDamage >= boots.getMaxDamage()) {
            // 靴子损坏 → 从剑中移除，停止冰霜行者和深海探索者效果
            stored.set(3, ItemStack.EMPTY);
            this.frostWalkerLevel = 0;
            this.depthStriderLevel = 0;
            this.playSound(SoundEvents.ITEM_BREAK.value(), 0.8F, 1.0F);
        } else {
            boots = boots.copy();
            boots.setDamageValue(newDamage);
            stored.set(3, boots);
        }

        // 写回剑的物品堆并同步
        swordStack = swordStack.copy();
        swordStack.set(ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.fromItems(stored));
        this.entityData.set(DATA_SWORD_STACK, swordStack);
    }

    /**
     * 快速检查弹射物附近（水平 ±2、向下 3 格内）是否存在水源。
     * 用于缩小生效范围，避免远离水面时无意义的每 tick 扫描。
     */
    private boolean isNearWater(ServerLevel level) {
        BlockPos center = this.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int checkRange = 2;
        int checkDepth = 3;

        for (int dx = -checkRange; dx <= checkRange; dx++) {
            for (int dz = -checkRange; dz <= checkRange; dz++) {
                for (int dy = 0; dy >= -checkDepth; dy--) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(mutable);
                    if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
                        return true;
                    }
                    if (!state.isAir() && !state.is(Blocks.WATER)) break; // 被方块遮挡，停止向下
                }
            }
        }
        return false;
    }

    /**
     * 在弹射物周围生成霜冰（冰霜行者效果）。
     * 仅在服务端调用，逐列向下扫描水面，模拟原版冰霜行者附魔的冻结逻辑。
     */
    private void applyFrostWalker(ServerLevel level) {
        BlockState frostedIce = Blocks.FROSTED_ICE.defaultBlockState();
        int range = Math.min(16, 1 + this.frostWalkerLevel);
        BlockPos center = this.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int maxDepth = range + 2; // 向下扫描深度

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx * dx + dz * dz > range * range) continue; // 圆形范围

                // 从弹射物高度向下扫描，寻找水面
                int scanStart = center.getY();
                int scanEnd = scanStart - maxDepth;
                for (int y = scanStart; y >= scanEnd; y--) {
                    mutable.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState current = level.getBlockState(mutable);

                    if (current.isAir()) continue; // 空气，继续向下

                    // 找到第一个非空气方块
                    if (current.is(Blocks.WATER) && current.getFluidState().isSource()) {
                        // 确保上方是空气（只冻结表面水）
                        BlockState above = level.getBlockState(mutable.above());
                        if (above.isAir()) {
                            level.setBlockAndUpdate(mutable, frostedIce);
                            level.scheduleTick(mutable, Blocks.FROSTED_ICE,
                                    Mth.nextInt(this.random, 60, 120));
                        }
                    }
                    break; // 遇到非空气方块后停止该列扫描
                }
            }
        }
    }

    /**
     * 检查主人是否适合接收返回的弹射物。
     * 主人必须存在、存活，且不是旁观模式的玩家。
     */
    private boolean isAcceptibleReturnOwner() {
        Entity entity = this.getOwner();
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof ServerPlayer serverPlayer && serverPlayer.isSpectator()) {
            return false;
        }
        return true;
    }

    @Override
    public void playerTouch(@NonNull Player player) {
        if (this.level().isClientSide() || (!this.ownedBy(player) && this.getOwner() != null)) {
            return;
        }

        if (this.isReturning()) {
            boolean pickedUp = false;

            if (this.pickup == Pickup.ALLOWED) {
                pickedUp = player.getInventory().add(this.getPickupItem());
            } else if (this.pickup == Pickup.CREATIVE_ONLY) {
                pickedUp = true;
            }

            if (pickedUp) {
                player.take(this, 1);
                this.discard();
            } else {
                if (this.pickup == Pickup.ALLOWED && !this.isRemoved()) {
                    this.spawnAtLocation((ServerLevel) this.level(), this.getPickupItem(), 0.1F);
                }
                this.discard();
            }
        } else {
            super.playerTouch(player);
        }
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player)
                || (this.isNoPhysics() && this.ownedBy(player)
                && player.getInventory().add(this.getPickupItem()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        Entity owner = this.getOwner();
        if (hit == owner) return;

        this.shouldReturn = true;

        if (!this.level().isClientSide()) {
            hit.hurt(this.damageSources().thrown(this, owner), damage);
            // 像三叉戟一样击中生物后停下来（大幅减速并反弹）
            this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        }
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    protected @NonNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT;
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        ItemStack swordStack = this.entityData.get(DATA_SWORD_STACK);
        if (!swordStack.isEmpty()) {
            output.store("SwordStack", ItemStack.CODEC, swordStack);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        ItemStack stack = input.read("SwordStack", ItemStack.CODEC)
                .orElse(this.getDefaultPickupItem());
        this.entityData.set(DATA_SWORD_STACK, stack);
    }
}

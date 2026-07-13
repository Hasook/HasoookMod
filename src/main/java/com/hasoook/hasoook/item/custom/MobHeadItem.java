package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.block.custom.MobHeadBlock;
import com.hasoook.hasoook.block.entity.custom.MobHeadBlockEntity;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class MobHeadItem extends BlockItem {
    public MobHeadItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * 设置这个头物品代表的实体类型。
     */
    public static void setEntityType(ItemStack stack, EntityType<?> entityType) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (key != null) {
            stack.set(ModDataComponents.MOB_HEAD_TYPE.get(), key.toString());
        }
    }

    /**
     * 设置这个头物品为玩家头（存储玩家名和 UUID 用于命名和皮肤）。
     */
    public static void setPlayerHead(ItemStack stack, Player player) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());
        if (key != null) {
            stack.set(ModDataComponents.MOB_HEAD_TYPE.get(), key.toString());
        }
        stack.set(ModDataComponents.HEAD_OWNER_NAME.get(), player.getName().getString());
        stack.set(ModDataComponents.HEAD_OWNER_UUID.get(), player.getStringUUID());
    }

    /**
     * 获取头物品的玩家名（仅 player 头有值）。
     */
    @Nullable
    public static String getHeadOwnerName(ItemStack stack) {
        return stack.get(ModDataComponents.HEAD_OWNER_NAME.get());
    }

    /**
     * 获取头物品的玩家 UUID 字符串（仅 player 头有值）。
     */
    @Nullable
    public static String getHeadOwnerUuid(ItemStack stack) {
        return stack.get(ModDataComponents.HEAD_OWNER_UUID.get());
    }

    /**
     * 获取这个头物品代表的实体类型，默认返回 null（渲染器会 fallback 到史蒂夫头模型）。
     */
    @Nullable
    public static EntityType<?> getEntityType(ItemStack stack) {
        String typeStr = stack.get(ModDataComponents.MOB_HEAD_TYPE.get());
        if (typeStr == null) return null;
        Identifier id = Identifier.tryParse(typeStr);
        if (id == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    /**
     * 当玩家手持生物头右键生物时：
     * <ul>
     *   <li>情况1：头物品还未记录类型 → 捕获目标生物的类型并存入 data component</li>
     *   <li>情况2：目标无头且头物品类型匹配 → 为生物复原头部</li>
     *   <li>情况3：目标无头且头物品类型不同 → 将外来头部移植到目标生物上</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof MobHeadItem)) return;

        // --- 情况1：头物品还没有记录类型 → 捕获目标生物类型 ---
        if (!stack.has(ModDataComponents.MOB_HEAD_TYPE.get())) {
            if (target instanceof Player targetPlayer) {
                // 玩家 → 存储玩家名和 UUID
                setPlayerHead(stack, targetPlayer);
            } else {
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
                if (key != null) {
                    stack.set(ModDataComponents.MOB_HEAD_TYPE.get(), key.toString());
                }
            }
            // ★ 挥手动画
            doSwing(player, event.getHand());
            event.setCanceled(true);
            return;
        }

        // --- 头物品已有类型，尝试为无头生物复原/移植头部 ---
        if (!target.hasData(ModAttachments.HEAD_REMOVED.get())
                || !Boolean.TRUE.equals(target.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        EntityType<?> headType = getEntityType(stack);
        if (headType == null) return;

        // 非创造模式玩家必须持有"可接头"标记的头才能接头
        if (!canAttachHead(player, stack)) return;

        Identifier headId = BuiltInRegistries.ENTITY_TYPE.getKey(headType);
        Identifier targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        // --- 情况2：类型匹配 → 复原原始头部 ---
        if (headId.equals(targetId)) {
            target.setData(ModAttachments.HEAD_REMOVED.get(), false);
            target.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(), "");
            stack.shrink(1);
            target.level().playSound(null, target.blockPosition(),
                    SoundEvents.SLIME_SQUISH, target.getSoundSource(), 1.0F, 0.8F);
            spawnAttachParticles(target);
            doSwing(player, event.getHand());
            awardAdvancement(player, "head_connector");
            event.setCanceled(true);
            return;
        }

        // --- 情况3：类型不同 → 移植外来头部 ---
        target.setData(ModAttachments.HEAD_REMOVED.get(), false);
        target.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), headId.toString());
        // 如果是玩家头，同步 UUID 和名称用于皮肤查询和物品显示
        String ownerUuid = getHeadOwnerUuid(stack);
        target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(),
                ownerUuid != null ? ownerUuid : "");
        String ownerName = getHeadOwnerName(stack);
        target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(),
                ownerName != null ? ownerName : "");
        stack.shrink(1);
        target.level().playSound(null, target.blockPosition(),
                SoundEvents.SLIME_SQUISH, target.getSoundSource(), 1.0F, 1.2F);
        spawnAttachParticles(target);
        doSwing(player, event.getHand());
        awardAdvancement(player, "head_connector");
        event.setCanceled(true);
    }

    /**
     * 潜行右键空气时：对自己使用生物头物品（复原或移植头部到自身）。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 必须潜行
        if (!player.isShiftKeyDown()) return;

        // ★ 如果玩家对准的是实体，由 onEntityInteract 处理，避免重复触发自身移植
        if (player.pick(5.0, 0.0F, false) instanceof EntityHitResult) {
            return;
        }

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof MobHeadItem)) return;

        // 必须已经记录了生物类型
        if (!stack.has(ModDataComponents.MOB_HEAD_TYPE.get())) return;

        // 玩家自己必须是无头状态
        if (!player.hasData(ModAttachments.HEAD_REMOVED.get())
                || !Boolean.TRUE.equals(player.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        EntityType<?> headType = getEntityType(stack);
        if (headType == null) return;

        // 非创造模式玩家必须持有"可接头"标记的头才能接头
        if (!canAttachHead(player, stack)) return;

        Identifier headId = BuiltInRegistries.ENTITY_TYPE.getKey(headType);
        Identifier playerId = BuiltInRegistries.ENTITY_TYPE.getKey(player.getType());

        if (headId.equals(playerId)) {
            // 类型匹配 → 复原自己的头
            player.setData(ModAttachments.HEAD_REMOVED.get(), false);
            player.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), "");
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(), "");
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(), "");
        } else {
            // 类型不同 → 移植外来头部到自身
            player.setData(ModAttachments.HEAD_REMOVED.get(), false);
            player.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), headId.toString());
            // 如果是玩家头，同步 UUID 和名称用于皮肤查询和物品显示
            String ownerUuid = getHeadOwnerUuid(stack);
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(),
                    ownerUuid != null ? ownerUuid : "");
            String ownerName = getHeadOwnerName(stack);
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(),
                    ownerName != null ? ownerName : "");
        }

        stack.shrink(1);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SLIME_SQUISH, player.getSoundSource(), 1.0F,
                headId.equals(playerId) ? 0.8F : 1.2F);

        // 粒子效果
        spawnAttachParticles(player);

        // ★ 挥手动画（RightClickItem 不会自动触发）
        doSwing(player, event.getHand());
        awardAdvancement(player, "head_connector");
        event.setCanceled(true);
    }

    /**
     * 授予进度。
     */
    private static void awardAdvancement(ServerPlayer player, String advancementName) {
        AdvancementHolder adv = player.level().getServer().getAdvancements().get(
                ResourceKey.create(Registries.ADVANCEMENT, Hasoook.id(advancementName)).identifier()
        );
        if (adv == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    /** 触发挥手动画（兼容 RightClickItem 不自动 swing 的情况）。 */
    private static void doSwing(ServerPlayer player, InteractionHand hand) {
        player.swing(hand);
        int action = hand == InteractionHand.MAIN_HAND
                ? ClientboundAnimatePacket.SWING_MAIN_HAND
                : ClientboundAnimatePacket.SWING_OFF_HAND;
        player.connection.send(new ClientboundAnimatePacket(player, action));
    }

    /**
     * 当生物头方块被玩家破坏时，手动生成带有正确数据的掉落物。
     * <p>
     * 这是必要的，因为此 NeoForge 版本的 BlockEntityRenderState API 尚未稳定，
     * 无法使用 loot table 的 copy_components。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof MobHeadBlock)) return;

        // 创造模式破坏方块不掉落物品
        if (event.getPlayer() != null && event.getPlayer().getAbilities().instabuild) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MobHeadBlockEntity headBe)) return;

        // 手动生成带数据的掉落物
        ItemStack drop = new ItemStack(event.getState().getBlock().asItem());
        String et = headBe.getEntityType();
        String pn = headBe.getPlayerName();
        String pu = headBe.getPlayerUuid();
        if (et != null && !et.isEmpty()) {
            drop.set(ModDataComponents.MOB_HEAD_TYPE.get(), et);
        }
        if (pn != null && !pn.isEmpty()) {
            drop.set(ModDataComponents.HEAD_OWNER_NAME.get(), pn);
        }
        if (pu != null && !pu.isEmpty()) {
            drop.set(ModDataComponents.HEAD_OWNER_UUID.get(), pu);
        }

        // 在方块位置生成掉落物
        net.minecraft.world.entity.item.ItemEntity itemEntity =
                new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
        level.addFreshEntity(itemEntity);
    }

    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        // 玩家头 → 显示 "XXX的头"
        String ownerName = getHeadOwnerName(stack);
        if (ownerName != null && !ownerName.isEmpty()) {
            return Component.translatable("item.hasoook.mob_head.player_named", ownerName);
        }
        // 普通生物头 → 显示 "XXX头"
        if (stack.has(ModDataComponents.MOB_HEAD_TYPE.get())) {
            EntityType<?> type = getEntityType(stack);
            if (type != null) {
                return Component.translatable("item.hasoook.mob_head.named", type.getDescription());
            }
        }
        return Component.translatable("item.hasoook.mob_head");
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay display, @NonNull Consumer<Component> tooltipAdder,
                                @NonNull TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        if (Boolean.TRUE.equals(stack.get(ModDataComponents.MOB_HEAD_ATTACHABLE.get()))) {
            tooltipAdder.accept(Component.translatable("tooltip.hasoook.mob_head.attachable")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    /** 在目标头部位置生成黏液粒子效果。 */
    private static void spawnAttachParticles(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel serverLevel)) return;
        double x = target.getX();
        double y = target.getY() + target.getEyeHeight() * 0.7;
        double z = target.getZ();
        for (int i = 0; i < 15; i++) {
            serverLevel.sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    x + (target.level().random.nextDouble() - 0.5) * 0.5,
                    y + (target.level().random.nextDouble() - 0.3) * 0.5,
                    z + (target.level().random.nextDouble() - 0.5) * 0.5,
                    1, 0.0, 0.0, 0.0, 0.0
            );
        }
    }

    /** 检查玩家是否有权接头（创造模式或拥有可接头标记的头物品）。 */
    private static boolean canAttachHead(ServerPlayer player, ItemStack stack) {
        return player.getAbilities().instabuild
                || Boolean.TRUE.equals(stack.get(ModDataComponents.MOB_HEAD_ATTACHABLE.get()));
    }

}
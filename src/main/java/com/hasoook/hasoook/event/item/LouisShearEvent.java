package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModAttachments;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.MobHeadItem;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class LouisShearEvent {

    /**
     * 对实体使用路易剪刀：剪掉目标生物的头（不能是玩家）。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 仅服务端处理
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 目标必须是生物，且不能是玩家
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (target instanceof Player) {
            return;
        }

        // 获取玩家手中的物品
        ItemStack stack = player.getItemInHand(event.getHand());

        // 必须是剪刀
        if (!(stack.getItem() instanceof ShearsItem)) {
            return;
        }

        // 必须附魔了"路易"
        int louisLevel = ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.LOUIS, stack);
        if (louisLevel <= 0) {
            return;
        }

        // 检查是否有移植的头部
        String transplantType = target.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
        boolean hasTransplant = transplantType != null && !transplantType.isEmpty();

        if (hasTransplant) {
            // --- 情况A：剪掉移植的头部 ---
            // ★ 先保存玩家头信息（在清空数据之前）
            String transplantPlayerUuid = target.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get());
            String transplantPlayerName = target.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get());

            target.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(), "");
            target.setData(ModAttachments.HEAD_REMOVED.get(), true);

            // ★ 剪头伤害
            applyHeadCutDamage(target, player);

            ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
            EntityType<?> transplantEntityType = BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.tryParse(transplantType));
            MobHeadItem.setEntityType(headStack,
                    transplantEntityType != null ? transplantEntityType : target.getType());

            // ★ 恢复玩家头 UUID（用于皮肤查询）
            if (transplantPlayerUuid != null && !transplantPlayerUuid.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_UUID.get(), transplantPlayerUuid);
            }
            // ★ 恢复玩家头名称（用于物品显示）
            if (transplantPlayerName != null && !transplantPlayerName.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_NAME.get(), transplantPlayerName);
            }

            target.spawnAtLocation((ServerLevel) target.level(), headStack, target.getEyeHeight());
            target.level().playSound(null, target.blockPosition(),
                    SoundEvents.SHEEP_SHEAR, target.getSoundSource(), 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, event.getHand().asEquipmentSlot());

            // ★ 挥手动画
            doSwing(player, event.getHand());
            awardAdvancement(player, "old_head");
            event.setCanceled(true);
            return;
        }

        // --- 情况B：正常剪头 ---
        if (target.hasData(ModAttachments.HEAD_REMOVED.get()) &&
                Boolean.TRUE.equals(target.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        target.setData(ModAttachments.HEAD_REMOVED.get(), true);

        // ★ 剪头伤害
        applyHeadCutDamage(target, player);

        ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
        MobHeadItem.setEntityType(headStack, target.getType());

        target.spawnAtLocation((ServerLevel) target.level(), headStack, target.getEyeHeight());
        target.level().playSound(null, target.blockPosition(),
                SoundEvents.SHEEP_SHEAR, target.getSoundSource(), 1.0F, 1.0F);
        stack.hurtAndBreak(1, player, event.getHand().asEquipmentSlot());

        // ★ 挥手动画
        doSwing(player, event.getHand());
        awardAdvancement(player, "old_head");
        event.setCanceled(true);
    }

    /**
     * 对空气潜行右键使用路易剪刀：剪掉玩家自己的头（或移植的头）。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        // ★ 如果玩家对准的是实体，由 onEntityInteract 处理，避免重复触发自身剪头
        if (player.pick(5.0, 0.0F, false) instanceof EntityHitResult) {
            return;
        }

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof ShearsItem)) {
            return;
        }

        int louisLevel = ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.LOUIS, stack);
        if (louisLevel <= 0) {
            return;
        }

        // --- 优先检查是否有移植的头部 ---
        String transplantType = player.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
        boolean hasTransplant = transplantType != null && !transplantType.isEmpty();

        if (hasTransplant) {
            // ★ 剪掉移植的外来头部
            EntityType<?> transplantEntityType = BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.tryParse(transplantType));

            // ★ 先保存玩家头信息（在清空数据之前）
            String transplantPlayerUuid = player.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get());
            String transplantPlayerName = player.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get());

            player.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), "");
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(), "");
            player.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(), "");
            player.setData(ModAttachments.HEAD_REMOVED.get(), true);

            // ★ 剪头伤害
            applyHeadCutDamage(player, player);

            ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
            if (transplantEntityType != null) {
                MobHeadItem.setEntityType(headStack, transplantEntityType);
            } else {
                MobHeadItem.setPlayerHead(headStack, player);
            }

            // 保留玩家头 UUID（如果移植的是玩家头）
            if (transplantPlayerUuid != null && !transplantPlayerUuid.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_UUID.get(), transplantPlayerUuid);
            }
            // 保留玩家头名称
            if (transplantPlayerName != null && !transplantPlayerName.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_NAME.get(), transplantPlayerName);
            }

            player.spawnAtLocation((ServerLevel) player.level(), headStack, player.getEyeHeight());
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.SHEEP_SHEAR, player.getSoundSource(), 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, event.getHand().asEquipmentSlot());

            doSwing(player, event.getHand());
            awardAdvancement(player, "old_head");
            event.setCanceled(true);
            return;
        }

        // --- 没有移植 → 剪自己的原生头 ---
        if (player.hasData(ModAttachments.HEAD_REMOVED.get()) &&
                Boolean.TRUE.equals(player.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        player.setData(ModAttachments.HEAD_REMOVED.get(), true);

        // ★ 剪头伤害
        applyHeadCutDamage(player, player);

        ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
        MobHeadItem.setPlayerHead(headStack, player);

        player.spawnAtLocation((ServerLevel) player.level(), headStack, player.getEyeHeight());
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHEEP_SHEAR, player.getSoundSource(), 1.0F, 1.0F);
        stack.hurtAndBreak(1, player, event.getHand().asEquipmentSlot());

        doSwing(player, event.getHand());
        awardAdvancement(player, "old_head");
        event.setCanceled(true);
    }

    /**
     * 根据配置对目标造成剪头伤害。
     * <p>
     * 伤害量 = 目标最大生命值 × {@code Config.HEAD_CUT_DAMAGE_PERCENT}。
     * 若配置值为 0 则跳过。
     */
    private static void applyHeadCutDamage(LivingEntity target, Player attacker) {
        float percent = Config.HEAD_CUT_DAMAGE_PERCENT.get().floatValue();
        if (percent <= 0.0F) return;
        float damage = target.getMaxHealth() * percent;
        if (damage > 0.0F) {
            target.hurt(target.level().damageSources().playerAttack(attacker), damage);
        }
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

    /**
     * 触发挥手动画。对于 RightClickItem 事件，客户端不会自动 swing，
     * 必须显式发送动画包给玩家自己。
     */
    private static void doSwing(ServerPlayer player, InteractionHand hand) {
        player.swing(hand);
        // 额外发送动画包给玩家自身，确保 RightClickItem 也能看到挥手
        int action = hand == InteractionHand.MAIN_HAND
                ? ClientboundAnimatePacket.SWING_MAIN_HAND
                : ClientboundAnimatePacket.SWING_OFF_HAND;
        player.connection.send(new ClientboundAnimatePacket(player, action));
    }

    /**
     * 当非玩家生物手持路易剪刀攻击玩家时，剪下玩家的头并造成伤害。
     * <p>
     * 在伤害计算阶段（Pre）拦截，将剪头伤害叠加到本次攻击伤害中，
     * 避免额外的 hurt() 调用引发递归。
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // 仅服务端处理
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;

        // 目标必须是玩家
        if (!(event.getEntity() instanceof Player target)) return;

        // 攻击者必须是非玩家的生物
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (attacker instanceof Player) return;

        // 攻击者主手必须持有剪刀
        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof ShearsItem)) return;

        // 必须附魔了"路易"
        int louisLevel = ModEnchantmentHelper.getEnchantmentLevel(ModEnchantments.LOUIS, stack);
        if (louisLevel <= 0) return;

        // --- 优先检查玩家是否有移植的头部 ---
        String transplantType = target.getData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get());
        boolean hasTransplant = transplantType != null && !transplantType.isEmpty();

        if (hasTransplant) {
            // ★ 剪掉移植的外来头部
            EntityType<?> transplantEntityType = BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.tryParse(transplantType));

            // ★ 先保存玩家头信息（在清空数据之前）
            String transplantPlayerUuid = target.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get());
            String transplantPlayerName = target.getData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get());

            target.setData(ModAttachments.TRANSPLANTED_HEAD_TYPE.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_UUID.get(), "");
            target.setData(ModAttachments.TRANSPLANTED_HEAD_PLAYER_NAME.get(), "");
            target.setData(ModAttachments.HEAD_REMOVED.get(), true);

            ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
            if (transplantEntityType != null) {
                MobHeadItem.setEntityType(headStack, transplantEntityType);
            } else {
                MobHeadItem.setPlayerHead(headStack, target);
            }

            // 保留玩家头 UUID（如果移植的是玩家头）
            if (transplantPlayerUuid != null && !transplantPlayerUuid.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_UUID.get(), transplantPlayerUuid);
            }
            // 保留玩家头名称
            if (transplantPlayerName != null && !transplantPlayerName.isEmpty()) {
                headStack.set(ModDataComponents.HEAD_OWNER_NAME.get(), transplantPlayerName);
            }

            target.spawnAtLocation(serverLevel, headStack, target.getEyeHeight());
            target.level().playSound(null, target.blockPosition(),
                    SoundEvents.SHEEP_SHEAR, target.getSoundSource(), 1.0F, 1.0F);

            // ★ 叠加剪头伤害到本次攻击伤害中
            addHeadCutDamageToEvent(event, target);
            return;
        }

        // --- 没有移植 → 剪玩家自己的原生头 ---
        if (target.hasData(ModAttachments.HEAD_REMOVED.get()) &&
                Boolean.TRUE.equals(target.getData(ModAttachments.HEAD_REMOVED.get()))) {
            return;
        }

        target.setData(ModAttachments.HEAD_REMOVED.get(), true);

        ItemStack headStack = new ItemStack(ModItems.MOB_HEAD.get());
        MobHeadItem.setPlayerHead(headStack, target);

        target.spawnAtLocation(serverLevel, headStack, target.getEyeHeight());
        target.level().playSound(null, target.blockPosition(),
                SoundEvents.SHEEP_SHEAR, target.getSoundSource(), 1.0F, 1.0F);

        // ★ 叠加剪头伤害到本次攻击伤害中
        addHeadCutDamageToEvent(event, target);
    }

    /**
     * 将剪头伤害百分比叠加到 LivingDamageEvent.Pre 的伤害值中。
     * <p>
     * 这样伤害来源保持为攻击者的攻击来源，而非额外的 sourceless 伤害。
     */
    private static void addHeadCutDamageToEvent(LivingDamageEvent.Pre event, LivingEntity target) {
        float percent = Config.HEAD_CUT_DAMAGE_PERCENT.get().floatValue();
        if (percent <= 0.0F) return;
        float extraDamage = target.getMaxHealth() * percent;
        if (extraDamage > 0.0F) {
            event.setNewDamage(event.getNewDamage() + extraDamage);
        }
    }
}

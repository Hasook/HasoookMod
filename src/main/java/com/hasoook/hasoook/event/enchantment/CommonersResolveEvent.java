package com.hasoook.hasoook.event.enchantment;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.enchantment.ModEnchantmentHelper;
import com.hasoook.hasoook.enchantment.ModEnchantments;
import com.hasoook.hasoook.network.payload.FpsPayload;
import com.hasoook.hasoook.network.manager.PlayerFpsManager;
import com.hasoook.hasoook.network.manager.PlayerWindowManager;
import com.hasoook.hasoook.network.payload.WindowSizePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = Hasoook.MOD_ID)
public class CommonersResolveEvent {

    private static int tickCounter = 0;

    /* ================= 客户端发送FPS与窗口大小 ================= */

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        int fps = mc.getFps();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        mc.getConnection().send(new FpsPayload(fps));
        mc.getConnection().send(new WindowSizePayload(width, height));
    }

    /* ================= 伤害事件 ================= */

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Pre event) {

        /* ================= 攻击方（武器触发） ================= */

        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {

            ItemStack weapon = attacker.getWeaponItem();

            int level = ModEnchantmentHelper.getEnchantmentLevel(
                    ModEnchantments.COMMONERS_RESOLVE, weapon
            );

            if (level > 0) {

                int fps = PlayerFpsManager.FPS_MAP
                        .getOrDefault(attacker.getUUID(), 60);

                float multiplier = calculateFpsMultiplier(fps);

                event.setNewDamage(event.getOriginalDamage() * multiplier);
            }
        }

        /* ================= 防御方（护甲触发） ================= */
        if (event.getEntity() instanceof ServerPlayer defender) {

            int totalLevel = 0;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {

                    ItemStack armor = defender.getItemBySlot(slot);

                    totalLevel += ModEnchantmentHelper.getEnchantmentLevel(
                            ModEnchantments.COMMONERS_RESOLVE,
                            armor
                    );
                }
            }

            if (totalLevel > 0) {

                int area = PlayerWindowManager.AREA_MAP
                        .getOrDefault(defender.getUUID(), 1920 * 1080);

                // 基础窗口倍率
                float baseMultiplier = calculateWindowMultiplier(area);

                // 转换为“最大可减伤比例”
                float maxReduction = 1.0f - baseMultiplier;

                // ===== 类原版叠加机制 =====
                // 每级 25% 强度
                // 4级封顶

                float strength = Mth.clamp(totalLevel * 0.25f, 0f, 1f);

                // 实际减伤
                float finalReduction = maxReduction * strength;

                // 转回倍率
                float finalMultiplier = 1.0f - finalReduction;

                event.setNewDamage(event.getNewDamage() * finalMultiplier);
            }
        }
    }

    // FPS曲线
    private static float calculateFpsMultiplier(int fps) {

        fps = Mth.clamp(fps, 1, 120);

        // ===== 超低帧硬锁 1000% 增伤 =====
        if (fps <= 10) {
            return 11.0f; // +1000%
        }

        float maxMultiplier = 10.0f; // 正常曲线最高 10x（+900%）

        if (fps <= 60) {

            float t = (60f - fps) / 50f; // 60->0 , 10->1
            float exponent = 2.2f;

            return 1.0f + (maxMultiplier - 1.0f) * (float)Math.pow(t, exponent);

        } else {

            float t = (fps - 60f) / 60f;

            // 平滑下降
            return 1.0f - (float)Math.pow(t, 2.0);
        }
    }

    // 窗口曲线
    private static float calculateWindowMultiplier(int area) {
        int mid = 1920 * 1080; // 1080p
        int max = 3840 * 2160; // 4K
        int min = 1;

        area = Mth.clamp(area, min, max);

        if (area <= mid) {
            float t = (float)(mid - area) / mid;

            float exponent = 3.0f; // 控制曲线弯曲程度，值越大前期越平缓、后期越爆发，值越小越接近线性
            float maxReduction = 0.99f;

            return 1.0f - maxReduction * (float)Math.pow(t, exponent);

        } else {
            float t = (float)(area - mid) / (max - mid);

            float exponent = 2.0f;
            float maxIncrease = 2.0f;

            return 1.0f + maxIncrease * (float)Math.pow(t, exponent);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = event.getItemStack();

        int level = ModEnchantmentHelper.getEnchantmentLevel(
                ModEnchantments.COMMONERS_RESOLVE, stack
        );

        if (level <= 0) return;

        int fps = mc.getFps();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        int area = width * height;

        /* ================= 武器 ================= */

        if (mc.player.getMainHandItem() == stack) {

            float multiplier = calculateFpsMultiplier(fps);
            float change = multiplier - 1f;

            event.getToolTip().add(Component.literal(""));
            event.getToolTip().add(
                    Component.literal("当前FPS: " + fps)
                            .withStyle(ChatFormatting.GRAY)
            );

            event.getToolTip().add(
                    Component.literal("造成伤害: " + toPercent(change))
                            .withStyle(change >= 0
                                    ? ChatFormatting.BLUE
                                    : ChatFormatting.RED)
            );
        }

        /* ================= 护甲 ================= */

        for (EquipmentSlot slot : EquipmentSlot.values()) {

            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR &&
                    mc.player.getItemBySlot(slot) == stack) {

                // 统计总等级（和伤害计算保持一致）
                int totalLevel = 0;

                for (EquipmentSlot s : EquipmentSlot.values()) {
                    if (s.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {

                        ItemStack armor = mc.player.getItemBySlot(s);

                        totalLevel += ModEnchantmentHelper.getEnchantmentLevel(
                                ModEnchantments.COMMONERS_RESOLVE,
                                armor
                        );
                    }
                }

                float baseMultiplier = calculateWindowMultiplier(area);
                float maxReduction = 1.0f - baseMultiplier;

                // 每级 25%，4级封顶
                float strength = Mth.clamp(totalLevel * 0.25f, 0f, 1f);
                float finalReduction = maxReduction * strength;
                float finalMultiplier = 1.0f - finalReduction;

                float change = finalMultiplier - 1f;

                event.getToolTip().add(Component.literal(""));
                event.getToolTip().add(
                        Component.literal("窗口: " + width + " × " + height)
                                .withStyle(ChatFormatting.GRAY)
                );

                event.getToolTip().add(
                        Component.literal("受到伤害: " + toPercent(change))
                                .withStyle(change <= 0
                                        ? ChatFormatting.BLUE
                                        : ChatFormatting.RED)
                );

                break;
            }
        }
    }

    private static String toPercent(float value) {
        return String.format("%+.2f%%", value * 100f);
    }
}
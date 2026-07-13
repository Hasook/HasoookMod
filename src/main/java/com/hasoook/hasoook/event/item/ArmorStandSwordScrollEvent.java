package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.custom.ArmorStandSwordItem;
import com.hasoook.hasoook.network.payload.SelectSwordSlotPayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 盔甲架剑 — 鼠标滚轮切换选中装备。
 * 在背包界面（AbstractContainerScreen）中鼠标悬停于盔甲架剑上滚动滚轮时，
 * 循环切换当前选中的装备槽位，并通过网络包同步到服务端。
 * 关闭界面时自动清除所有选中状态（模仿收纳袋行为）。
 */
@EventBusSubscriber(modid = Hasoook.MOD_ID, value = Dist.CLIENT)
public class ArmorStandSwordScrollEvent {

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return;

        ItemStack stack = hoveredSlot.getItem();
        if (!(stack.getItem() instanceof ArmorStandSwordItem)) return;

        // 没有任何装备时不响应滚轮
        ItemContainerContents icc = stack.getOrDefault(
                ModDataComponents.ARMOR_STAND_SWORD_CONTENTS.get(),
                ItemContainerContents.EMPTY);
        if (icc.stream().allMatch(ItemStack::isEmpty)) return;

        double scrollDeltaY = event.getScrollDeltaY();
        if (scrollDeltaY == 0) return;

        // 向上滚动 → 上一个装备，向下滚动 → 下一个装备
        int direction = scrollDeltaY > 0 ? -1 : 1;
        ArmorStandSwordItem.cycleSelectedSlot(stack, direction);

        // 同步到服务端
        int selectedSlot = stack.getOrDefault(ModDataComponents.SELECTED_SLOT.get(), -1);
        ClientPacketDistributor.sendToServer(new SelectSwordSlotPayload(hoveredSlot.index, selectedSlot));

        // 滚轮音效反馈
        if (screen.getMinecraft().player != null) {
            screen.getMinecraft().player.playSound(
                    SoundEvents.BUNDLE_REMOVE_ONE, 0.6F,
                    0.7F + screen.getMinecraft().level.random.nextFloat() * 0.4F);
        }

        event.setCanceled(true);
    }

    /**
     * 关闭容器界面时，清除所有盔甲架剑的滚轮选中状态。
     * 模仿收纳袋关闭背包后取消选中的行为。
     */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.getMinecraft().player == null) return;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof ArmorStandSwordItem)) continue;

            Integer selected = stack.get(ModDataComponents.SELECTED_SLOT.get());
            if (selected != null && selected >= 0) {
                // 清除本地选中状态
                ArmorStandSwordItem.applySelectedSlot(stack, -1);
                // 同步到服务端
                ClientPacketDistributor.sendToServer(new SelectSwordSlotPayload(slot.index, -1));
            }
        }
    }
}

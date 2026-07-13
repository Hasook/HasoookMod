package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RedEnvelopeMenu extends AbstractContainerMenu {
    // 创建一个临时的、只有1个格子的物品栏用于放置放入红包的物品
    public final SimpleContainer inventory = new SimpleContainer(1);

    // 客户端构造函数
    public RedEnvelopeMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv);
    }

    // 服务端构造函数
    public RedEnvelopeMenu(int containerId, Inventory inv) {
        super(ModMenuTypes.RED_ENVELOPE_MENU.get(), containerId);

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        this.addSlot(new Slot(this.inventory, 0, 80, 35));
    }


    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        // 因为是物品打开的界面，只要玩家活着拿着钻石就有效 (或者简单返回 true)
        return true;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
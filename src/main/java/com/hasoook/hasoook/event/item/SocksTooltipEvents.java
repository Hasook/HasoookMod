package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.component.ModDataComponents;
import com.hasoook.hasoook.item.ModItems;
import com.hasoook.hasoook.item.custom.SocksItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class SocksTooltipEvents {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!(stack.getItem() instanceof SocksItem)) return;

            int wear = stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
            int stage = SocksItem.getStage(wear);
            lines.add(SocksItem.getStageTooltip(stage));
        });
    }
}

package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class SlimeballSpearTooltipEvents {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        if (!stack.is(ModItems.SLIME_SPEAR.get())) return;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag tag = data.copyTag();
        if (!tag.contains("AttachedItem")) return;

        String id = tag.getString("AttachedItem").orElse("");
        if (id.isEmpty()) return;

        Item item = BuiltInRegistries.ITEM
                .get(Identifier.parse(id))
                .map(Holder.Reference::value)
                .orElse(Items.AIR);

        // 获取物品名字
        MutableComponent itemName = Component.translatable(item.getDescriptionId()).plainCopy();

        // 空行分隔
        event.getToolTip().add(Component.literal(""));

        // 显示存储的物品名字
        MutableComponent slimeLine = Component.translatable(
                "tooltip.hasoook.slime_attached",
                itemName
        ).withStyle(style -> style.withColor(ChatFormatting.GREEN)); // 绿色
        event.getToolTip().add(slimeLine);
    }
}

package com.hasoook.hasoook.event.item;

import com.hasoook.hasoook.item.ModItems;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class SlimeballSpearTooltipEvents {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!stack.isOf(ModItems.SLIME_SPEAR)) return;

            NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (data == null) return;

            NbtCompound tag = data.copyNbt();
            if (!tag.contains("AttachedItem")) return;

            String id = tag.getString("AttachedItem", "");
            if (id.isEmpty()) return;

            Item item = Registries.ITEM.getOptionalValue(Identifier.of(id)).orElse(Items.AIR);

            MutableText itemName = Text.translatable(item.getTranslationKey()).copy();

            lines.add(Text.literal(""));

            MutableText slimeLine = Text.translatable(
                    "tooltip.hasoook.slime_attached",
                    itemName
            ).styled(style -> style.withColor(Formatting.GREEN));
            lines.add(slimeLine);
        });
    }
}

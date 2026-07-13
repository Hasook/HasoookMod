package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.block.ModBlocks;
import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class PhantomLampBlockItem extends BlockItem {
    public static final String STATE_PRISTINE = "pristine";
    public static final String STATE_BROKEN = "broken";
    public static final String STATE_REPAIRED = "repaired";

    public PhantomLampBlockItem(Properties properties) {
        super(ModBlocks.PHANTOM_LAMP.get(), properties.rarity(Rarity.UNCOMMON).stacksTo(1));
    }

    public static String getState(ItemStack stack) {
        String state = stack.get(ModDataComponents.PHANTOM_LAMP_STATE.get());
        return state != null ? state : STATE_PRISTINE;
    }

    public static void setState(ItemStack stack, String state) {
        stack.set(ModDataComponents.PHANTOM_LAMP_STATE.get(), state);
    }

    public static boolean isPristine(ItemStack stack) {
        return STATE_PRISTINE.equals(getState(stack));
    }

    public static boolean isBroken(ItemStack stack) {
        return STATE_BROKEN.equals(getState(stack));
    }

    public static boolean isRepaired(ItemStack stack) {
        return STATE_REPAIRED.equals(getState(stack));
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context,
                                @NonNull TooltipDisplay tooltipDisplay, @NonNull Consumer<Component> tooltipAdder,
                                @NonNull TooltipFlag flag) {
        String state = getState(stack);
        switch (state) {
            case STATE_PRISTINE ->
                    tooltipAdder.accept(Component.translatable("tooltip.hasoook.phantom_lamp.pristine"));
            case STATE_BROKEN ->
                    tooltipAdder.accept(Component.translatable("tooltip.hasoook.phantom_lamp.broken"));
            case STATE_REPAIRED ->
                    tooltipAdder.accept(Component.translatable("tooltip.hasoook.phantom_lamp.repaired"));
        }
    }

    public boolean isFoil(ItemStack stack) {
        return STATE_PRISTINE.equals(getState(stack));
    }

}

package com.hasoook.hasoook.item.custom;

import com.hasoook.hasoook.component.ModDataComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SocksItem extends Item {

    private static final int STAGE_0_MAX = 199;
    private static final int STAGE_1_MAX = 499;
    private static final int STAGE_2_MAX = 999;
    private static final int STAGE_3_MAX = 1799;
    private static final int STAGE_4_MAX = 2999;
    private static final int STAGE_5_MAX = 4999;

    public SocksItem(Settings settings) {
        super(settings);
    }

    public static int getStage(int wear) {
        if (wear <= STAGE_0_MAX) return 0;
        if (wear <= STAGE_1_MAX) return 1;
        if (wear <= STAGE_2_MAX) return 2;
        if (wear <= STAGE_3_MAX) return 3;
        if (wear <= STAGE_4_MAX) return 4;
        if (wear <= STAGE_5_MAX) return 5;
        return 6;
    }

    public static Text getStageTooltip(int stage) {
        return switch (stage) {
            case 0 -> Text.translatable("tooltip.hasoook.socks.fresh")
                    .formatted(Formatting.GRAY);
            case 1 -> Text.translatable("tooltip.hasoook.socks.slight_odor")
                    .formatted(Formatting.GRAY);
            case 2 -> Text.translatable("tooltip.hasoook.socks.sweat_stench")
                    .formatted(Formatting.GREEN);
            case 3 -> Text.translatable("tooltip.hasoook.socks.nose_burning")
                    .formatted(Formatting.GREEN);
            case 4 -> Text.translatable("tooltip.hasoook.socks.suffocating")
                    .formatted(Formatting.DARK_GREEN);
            case 5 -> Text.translatable("tooltip.hasoook.socks.bioweapon")
                    .formatted(Formatting.DARK_GREEN, Formatting.BOLD);
            case 6 -> Text.translatable("tooltip.hasoook.socks.indescribable")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD);
            default -> Text.empty();
        };
    }

    public static int getMinWearForStage(int stage) {
        if (stage <= 0) return 0;
        if (stage >= STAGE_MIN_WEAR.length) stage = STAGE_MIN_WEAR.length - 1;
        return STAGE_MIN_WEAR[stage];
    }

    private static final int[] STAGE_MIN_WEAR = {
            0, 200, 500, 1000, 1800, 3000, 5000,
    };

    /** Convenience getter for SOCKS_WEAR component */
    public static int getWear(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOCKS_WEAR, 0);
    }

    /** Convenience setter for SOCKS_WEAR component */
    public static void setWear(ItemStack stack, int wear) {
        stack.set(ModDataComponents.SOCKS_WEAR, wear);
    }
}

package com.hasoook.hasoook.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 完全去掉装备在 BundleTooltip 中的进度条和多余空白。
 */
@Mixin(ClientBundleTooltip.class)
public class ClientBundleTooltipMixin {

    @Shadow
    @Final
    private BundleContents contents;

    /** EQUIPPABLE 组件 = 装备物品（头盔/胸甲/护腿/靴子/头颅/鞘翅等） */
    private boolean hasoook$isEquipment() {
        return this.contents.itemCopyStream()
                .allMatch(s -> s.get(DataComponents.EQUIPPABLE) != null);
    }

    // ── 进度条填充 ──────────────────────────────────────────────

    @Inject(method = "getProgressBarFill", at = @At("HEAD"), cancellable = true)
    private void fixFill(CallbackInfoReturnable<Integer> cir) {
        if (hasoook$isEquipment())
            cir.setReturnValue(Mth.clamp(this.contents.size() * 94 / 4, 0, 94));
    }

    // ── 进度条文字（去掉"满"） ──────────────────────────────────

    @Inject(method = "getProgressBarFillText", at = @At("HEAD"), cancellable = true)
    private void fixText(CallbackInfoReturnable<?> cir) {
        if (hasoook$isEquipment()) cir.setReturnValue(null);
    }

    // ── 不画进度条 ──────────────────────────────────────────────

    @Inject(method = "drawProgressbar", at = @At("HEAD"), cancellable = true)
    private void skipDraw(int x, int y, Font font, GuiGraphics g, CallbackInfo ci) {
        if (hasoook$isEquipment()) ci.cancel();
    }

    // ── 去掉进度条占用的高度（getHeight 内部会调用 backgroundHeight） ──

    @Inject(method = "backgroundHeight", at = @At("RETURN"), cancellable = true)
    private void fixBgHeight(CallbackInfoReturnable<Integer> cir) {
        if (hasoook$isEquipment()) {
            int rows = Mth.positiveCeilDiv(Math.min(12, this.contents.size()), 4);
            cir.setReturnValue(rows * 24); // 去掉 13+8 的进度条区域，只保留物品网格
        }
    }
}

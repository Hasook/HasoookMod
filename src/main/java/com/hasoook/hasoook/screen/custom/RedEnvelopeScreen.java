package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.Hasoook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class RedEnvelopeScreen extends AbstractContainerScreen<RedEnvelopeMenu> {

    private static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/gui/red_envelope/red_envelope_gui.png");

    private EditBox amountBox;
    private EditBox passwordBox;
    private Button typeButton;

    private enum EnvelopeType {
        NORMAL("普通红包"),
        LUCKY("拼手气红包");

        final String displayName;
        EnvelopeType(String name) { this.displayName = name; }

        public EnvelopeType next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private EnvelopeType currentType = EnvelopeType.NORMAL;

    public RedEnvelopeScreen(RedEnvelopeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;

        this.titleLabelY = 6;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // ⭐ 红包类型按钮（左上）
        typeButton = Button.builder(Component.literal(currentType.displayName), button -> {
            currentType = currentType.next();
            button.setMessage(Component.literal(currentType.displayName));
        }).bounds(x + 20, y + 18, 70, 20).build();
        this.addRenderableWidget(typeButton);

        // ⭐ 数量输入框（右上）
        amountBox = new EditBox(this.font, x + 105, y + 22, 50, 16, Component.literal("数量"));
        amountBox.setValue("1");
        this.addRenderableWidget(amountBox);

        // ⭐ 口令输入框（中下，默认可见）
        passwordBox = new EditBox(this.font, x + 20, y + 56, 136, 16, Component.literal("口令 (可选)"));
        passwordBox.setMaxLength(32);
        this.addRenderableWidget(passwordBox);

        // ⭐ 发送按钮
        this.addRenderableWidget(Button.builder(Component.literal("发送"), button -> {
            sendRedEnvelope();
        }).bounds(x + 30, y + 78, 50, 18).build());

        // ⭐ 取消按钮
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> {
            this.onClose();
        }).bounds(x + 96, y + 78, 50, 18).build());
    }

    private void sendRedEnvelope() {
        int amount;

        try {
            amount = Integer.parseInt(amountBox.getValue());
            if (amount <= 0) return;
        } catch (NumberFormatException e) {
            return;
        }

        String password = passwordBox.getValue().trim();

        // ⭐ 自动逻辑：是否为口令红包
        boolean hasPassword = !password.isEmpty();

        // TODO 发送给服务端
        // sendPacket(type, amount, password, hasPassword);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE,
                x, y, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.drawString(this.font, "红包类型:", x + 20, y + 8, 0x404040, false);
        guiGraphics.drawString(this.font, "数量:", x + 105, y + 12, 0x404040, false);

        // ⭐ 口令提示
        if (passwordBox.getValue().isEmpty()) {
            guiGraphics.drawString(this.font,
                    "不填则为普通红包",
                    x + 20,
                    y + 45,
                    0x808080,
                    false);
        }
    }
}
package com.hasoook.hasoook.screen.custom;

import com.hasoook.hasoook.Hasoook;
import com.hasoook.hasoook.network.payload.ConsumeDyePayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.awt.image.BufferedImage;
import java.util.*;

import static com.hasoook.hasoook.screen.custom.PaintProcessor.getActiveQuestDisplayName;

public class PaintScreen extends Screen {
    private static final int GRID_SIZE = 64;
    private static final int EMPTY_PIXEL = 0x00000000;
    public static final int CANVAS_BG_COLOR = 0xFFEEEEEE;

    private static final int MIN_PIXEL_SIZE = 3, MAX_PIXEL_SIZE = 12;
    private static final int FIXED_SWATCH_SIZE = 18;
    private static final int BTN_WIDTH = 120, BTN_HEIGHT = 20;

    private static final int CLOSE_BTN_SIZE = 18;
    private static final int CLOSE_BTN_OFFSET_X = 8;
    private static final int CLOSE_BTN_OFFSET_Y = 8;

    private static final int TOOL_BTN_SIZE = 20;
    private static final int TOOL_GAP = 4;

    private static final int MAX_UNDO_HISTORY = 50;
    private static final int MISSING_DYE_MSG_DURATION = 100;
    private static final int HELP_BTN_SIZE = 16;

    private static final Identifier PAINT_BG = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/gui/paint_bg.png");

    private static final Identifier ICON_BRUSH = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/gui/icon_brush.png");
    private static final Identifier ICON_ERASER = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/gui/icon_eraser.png");
    private static final Identifier ICON_BUCKET = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "textures/gui/icon_bucket.png");

    private static final int[] PALETTE = {
            0xFF1D1D21, 0xFFF9FFFE, 0xFF474F52, 0xFF9D9D97,
            0xFF835432, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF5E7C16, 0xFF80C71F, 0xFF169C9C, 0xFF3AB3DA,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA
    };

    private static final Map<Integer, Item> COLOR_DYE_MAP = new HashMap<>();
    static {
        COLOR_DYE_MAP.put(0xFF1D1D21, Items.BLACK_DYE);
        COLOR_DYE_MAP.put(0xFFF9FFFE, Items.WHITE_DYE);
        COLOR_DYE_MAP.put(0xFF474F52, Items.GRAY_DYE);
        COLOR_DYE_MAP.put(0xFF9D9D97, Items.LIGHT_GRAY_DYE);
        COLOR_DYE_MAP.put(0xFF835432, Items.BROWN_DYE);
        COLOR_DYE_MAP.put(0xFFB02E26, Items.RED_DYE);
        COLOR_DYE_MAP.put(0xFFF9801D, Items.ORANGE_DYE);
        COLOR_DYE_MAP.put(0xFFFED83D, Items.YELLOW_DYE);
        COLOR_DYE_MAP.put(0xFF5E7C16, Items.GREEN_DYE);
        COLOR_DYE_MAP.put(0xFF80C71F, Items.LIME_DYE);
        COLOR_DYE_MAP.put(0xFF169C9C, Items.CYAN_DYE);
        COLOR_DYE_MAP.put(0xFF3AB3DA, Items.LIGHT_BLUE_DYE);
        COLOR_DYE_MAP.put(0xFF3C44AA, Items.BLUE_DYE);
        COLOR_DYE_MAP.put(0xFF8932B8, Items.PURPLE_DYE);
        COLOR_DYE_MAP.put(0xFFC74EBD, Items.MAGENTA_DYE);
        COLOR_DYE_MAP.put(0xFFF38BAA, Items.PINK_DYE);
    }

    private enum Tool { BRUSH, ERASER, BUCKET }

    private final int[][] pixels = new int[GRID_SIZE][GRID_SIZE];
    private boolean isDrawing;
    private Integer currentColor;
    private int activeColor = EMPTY_PIXEL;
    private int lastX = -1, lastY = -1;
    private int pSize, canvasSize, startX, startY, palX, palY;
    private int closeBtnX, closeBtnY;

    private Tool currentTool = Tool.BRUSH;
    private int toolX, toolStartY;

    // 画布上的缺失染料提示
    private String missingDyeMessage = null;
    private int missingDyeMessageTicks = 0;

    // 帮助按钮位置
    private int helpBtnX, helpBtnY;

    // 撤销 / 重做
    private final Deque<int[][]> undoStack = new ArrayDeque<>();
    private final Deque<int[][]> redoStack = new ArrayDeque<>();

    private NativeImage canvasImage;
    private DynamicTexture canvasTexture;
    private Identifier canvasTextureId;
    private boolean textureDirty = false;

    public PaintScreen() {
        super(Component.literal("画板识别"));
        clearCanvas();
    }

    @Override
    protected void init() {
        super.init();
        // 提前开始加载物品纹理，避免识别时等待
        PaintProcessor.preloadTexturesAsync();
        int gap = 15, palW = FIXED_SWATCH_SIZE * 2, palH = FIXED_SWATCH_SIZE * 8, textAreaWidth = 60;
        int availW = width - palW - textAreaWidth - 2 * gap;
        int availH = height - BTN_HEIGHT - 2 * gap;

        pSize = Math.max(MIN_PIXEL_SIZE, Math.min(MAX_PIXEL_SIZE, Math.min(availW, availH) / GRID_SIZE));
        canvasSize = GRID_SIZE * pSize;

        int cx = width / 2, cy = height / 2;
        startX = cx - canvasSize / 2;
        startY = cy - canvasSize / 2 - 20;
        palX = startX - gap - palW;
        palY = startY + (canvasSize - palH) / 2;

        int btnX = startX + (canvasSize - BTN_WIDTH) / 2;
        int btnY = startY + canvasSize + gap;

        closeBtnX = startX + canvasSize + CLOSE_BTN_OFFSET_X + 5;
        closeBtnY = startY + CLOSE_BTN_OFFSET_Y;

        toolX = startX + canvasSize + 15;
        toolStartY = startY + 40;

        helpBtnX = 10;
        helpBtnY = 10;

        currentColor = getFirstAvailableColor();

        if (this.canvasImage == null) {
            this.canvasImage = new NativeImage(GRID_SIZE, GRID_SIZE, false);
            this.canvasTexture = new DynamicTexture(() -> "hasoook_paint_canvas", this.canvasImage);
            this.canvasTextureId = Identifier.fromNamespaceAndPath(Hasoook.MOD_ID, "paint_canvas_" + UUID.randomUUID().toString().replace("-", ""));
            Minecraft.getInstance().getTextureManager().register(this.canvasTextureId, this.canvasTexture);
            refreshEntireTexture();
        }

        addRenderableWidget(Button.builder(Component.literal("完成绘制"), b -> onFinishDrawing())
                .bounds(btnX, btnY, BTN_WIDTH, BTN_HEIGHT).build());
    }

    @Override
    public void removed() {
        super.removed();
        if (this.canvasTexture != null) {
            this.canvasTexture.close();
            Minecraft.getInstance().getTextureManager().release(this.canvasTextureId);
        }
    }

    private void setCanvasPixel(int x, int y, int argbColor) {
        if (!inBounds(x, y)) return;
        pixels[x][y] = argbColor;

        if (this.canvasImage != null) {
            if (argbColor == EMPTY_PIXEL) {
                this.canvasImage.setPixelABGR(x, y, 0);
            } else {
                this.canvasImage.setPixel(x, y, argbColor);
            }
            this.textureDirty = true;
        }
    }

    private void refreshEntireTexture() {
        if (this.canvasImage == null) return;
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                setCanvasPixel(x, y, pixels[x][y]);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);

        if (isDrawing) {
            int[] pos = getPixel(mx, my);
            if (pos != null) {
                drawLine(lastX, lastY, pos[0], pos[1]);
                lastX = pos[0];
                lastY = pos[1];
            }
        }

        renderPalette(g);
        renderCanvas(g);
        renderMissingDyeMessage(g);
        renderHelpButton(g, mx, my);
        renderHandDrawnCloseButton(g, mx, my);

        List<Component> tooltipToRender = renderToolbar(g, mx, my);

        String questName = PaintProcessor.getActiveQuestName();
        if (questName != null && hasAnyDye()) {
            Component questMsg = Component.literal("§d颜料在低语：它们想成为" + getActiveQuestDisplayName() + "！");
            int textWidth = font.width(questMsg);

            float time = Util.getMillis() / 800.0F;
            float offsetY = (float) (Math.sin(time) * 1.5F + Math.sin(time * 1.3F + 1.0F) * 0.5F);
            float offsetX = (float) (Math.cos(time * 0.9F) * 1.5F);

            int textX = startX + (canvasSize - textWidth) / 2;
            int textY = startY + 10;

            g.pose().pushMatrix();
            g.pose().translate(offsetX, offsetY);
            g.drawString(font, questMsg, textX, textY, 0xFFFFFFFF);
            g.pose().popMatrix();
        }

        super.render(g, mx, my, pt);

        // 帮助按钮的 tooltip 优先显示
        if (isMouseOverHelpButton(mx, my)) {
            List<FormattedCharSequence> helpLines = new ArrayList<>();
            for (Component c : getHelpTooltip()) {
                helpLines.add(c.getVisualOrderText());
            }
            g.setTooltipForNextFrame(font, helpLines, mx, my);
        } else if (tooltipToRender != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : tooltipToRender) {
                lines.add(c.getVisualOrderText());
            }
            g.setTooltipForNextFrame(font, lines, mx, my);
        }
    }

    private List<Component> renderToolbar(GuiGraphics g, int mx, int my) {
        List<Component> tooltip = null;
        Tool[] tools = Tool.values();

        int panelPadding = 4;
        int panelW = TOOL_BTN_SIZE + panelPadding * 2;
        int panelH = tools.length * TOOL_BTN_SIZE + (tools.length - 1) * TOOL_GAP + panelPadding * 2;

        g.fill(toolX - panelPadding, toolStartY - panelPadding, toolX - panelPadding + panelW, toolStartY - panelPadding + panelH, 0xAA222222);
        drawOutline(g, toolX - panelPadding, toolStartY - panelPadding, panelW, panelH, 0xFF444444);

        for (int i = 0; i < tools.length; i++) {
            Tool t = tools[i];
            int y = toolStartY + i * (TOOL_BTN_SIZE + TOOL_GAP);
            boolean isHovered = mx >= toolX && mx < toolX + TOOL_BTN_SIZE && my >= y && my < y + TOOL_BTN_SIZE;
            boolean isSelected = currentTool == t;

            int bgColor = isSelected ? 0xAA3B6085 : (isHovered ? 0x99555555 : 0x66111111);
            g.fill(toolX, y, toolX + TOOL_BTN_SIZE, y + TOOL_BTN_SIZE, bgColor);

            int borderColor = isSelected ? 0xFFFFAA00 : (isHovered ? 0xFFFFFFFF : 0xFF222222);
            drawOutline(g, toolX, y, TOOL_BTN_SIZE, TOOL_BTN_SIZE, borderColor);

            Identifier iconTex;
            switch(t) {
                case BRUSH: iconTex = ICON_BRUSH; break;
                case ERASER: iconTex = ICON_ERASER; break;
                case BUCKET: iconTex = ICON_BUCKET; break;
                default: iconTex = ICON_BRUSH;
            }

            int iconSize = 16;
            int offset = (TOOL_BTN_SIZE - iconSize) / 2;

            g.blit(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    iconTex,
                    toolX + offset,
                    y + offset,
                    0.0F,
                    0.0F,
                    iconSize,
                    iconSize,
                    16,
                    16
            );

            if (isHovered) {
                tooltip = getToolTooltip(t);
            }
        }
        return tooltip;
    }

    private List<Component> getToolTooltip(Tool tool) {
        List<Component> tooltip = new ArrayList<>();
        switch(tool) {
            case BRUSH:
                tooltip.add(Component.literal("画笔"));
                tooltip.add(Component.literal("§7左键: 绘制"));
                tooltip.add(Component.literal("§7右键: 擦除"));
                tooltip.add(Component.literal("§7中键: 填充"));
                break;
            case ERASER:
                tooltip.add(Component.literal("橡皮擦"));
                tooltip.add(Component.literal("§7左键: 擦除"));
                break;
            case BUCKET:
                tooltip.add(Component.literal("油漆桶"));
                tooltip.add(Component.literal("§7左键: 填充"));
                break;
        }
        return tooltip;
    }

    private void renderHandDrawnCloseButton(GuiGraphics g, int mx, int my) {
        boolean hover = isMouseOverCloseButton(mx, my);
        int bg = hover ? 0xFFFF5A5A : 0xFFE23B3B;
        int border = hover ? 0xFFFFFFFF : 0xFF2B0000;

        g.fill(closeBtnX, closeBtnY, closeBtnX + CLOSE_BTN_SIZE, closeBtnY + CLOSE_BTN_SIZE, bg);
        drawOutline(g, closeBtnX, closeBtnY, CLOSE_BTN_SIZE, CLOSE_BTN_SIZE, border);

        int x1 = closeBtnX + 4, y1 = closeBtnY + 4;
        int x2 = closeBtnX + CLOSE_BTN_SIZE - 5, y2 = closeBtnY + CLOSE_BTN_SIZE - 5;

        drawPixelLine(g, x1, y1, x2, y2, 0xFFFFFFFF);
        drawPixelLine(g, x1, y2, x2, y1, 0xFFFFFFFF);
        drawPixelLine(g, x1 + 1, y1, x2 + 1, y2, 0xCCFFFFFF);
        drawPixelLine(g, x1 + 1, y2, x2 + 1, y1, 0xCCFFFFFF);
    }

    private void drawPixelLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private boolean isMouseOverCloseButton(double mx, double my) {
        return mx >= closeBtnX && mx < closeBtnX + CLOSE_BTN_SIZE && my >= closeBtnY && my < closeBtnY + CLOSE_BTN_SIZE;
    }

    private void renderPalette(GuiGraphics g) {
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2, row = i / 2;
            int x = palX + col * FIXED_SWATCH_SIZE, y = palY + row * FIXED_SWATCH_SIZE;
            int color = PALETTE[i];

            g.fill(x, y, x + FIXED_SWATCH_SIZE, y + FIXED_SWATCH_SIZE, color);

            if (!canUseColor(color)) {
                g.fill(x, y, x + FIXED_SWATCH_SIZE, y + FIXED_SWATCH_SIZE, 0xCC222222);
                int lockW = 8, lockH = 6, lx = x + 5, ly = y + 9;
                drawOutline(g, lx + 2, ly - 3, 4, 4, 0xFFAAAAAA);
                g.fill(lx, ly, lx + lockW, ly + lockH, 0xFFAAAAAA);
                g.fill(lx + 3, ly + 2, lx + 5, ly + 4, 0xFF333333);
            }

            if (Objects.equals(currentColor, color)) {
                drawOutline(g, x, y, FIXED_SWATCH_SIZE, FIXED_SWATCH_SIZE, 0xFFFFFFFF);
                drawOutline(g, x + 1, y + 1, FIXED_SWATCH_SIZE - 2, FIXED_SWATCH_SIZE - 2, 0xFF000000);
            }
        }
    }

    private void renderCanvas(GuiGraphics g) {
        int decorationSize = 2;
        int bgOffset = decorationSize * pSize;

        g.blit(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                PAINT_BG,
                startX - bgOffset,
                startY - bgOffset,
                0.0F,
                0.0F,
                canvasSize + (bgOffset * 2),
                canvasSize + (bgOffset * 2),
                64,
                64,
                64,
                64
        );

        if (this.textureDirty && this.canvasTexture != null) {
            this.canvasTexture.upload();
            this.textureDirty = false;
        }

        if (this.canvasTextureId != null) {
            g.blit(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    this.canvasTextureId,
                    startX, startY,
                    0.0F, 0.0F,
                    canvasSize, canvasSize,
                    canvasSize, canvasSize
            );
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        double mx = e.x(), my = e.y();
        int btn = e.button();

        if (btn == 0 && isMouseOverCloseButton(mx, my)) {
            onClose();
            return true;
        }

        if (btn == 0 && handleToolClick(mx, my)) {
            return true;
        }

        if (btn == 0 && handlePaletteClick(mx, my)) return true;

        int[] pos = getPixel(mx, my);
        if (pos == null) return super.mouseClicked(e, dbl);

        // 在修改像素前保存当前状态用于撤销
        saveUndoState();

        boolean doFill = (btn == 2) || (btn == 0 && currentTool == Tool.BUCKET);
        boolean doErase = (btn == 1) || (btn == 0 && currentTool == Tool.ERASER);
        boolean doDraw = (btn == 0 && currentTool == Tool.BRUSH);

        if (doFill) {
            if (currentColor != null) {
                floodFillRaw(pos[0], pos[1], pixels[pos[0]][pos[1]], currentColor);
            }
            return true;
        } else if (doErase) {
            activeColor = EMPTY_PIXEL;
        } else if (doDraw) {
            activeColor = currentColor == null ? EMPTY_PIXEL : currentColor;
        } else {
            return true;
        }

        setCanvasPixel(pos[0], pos[1], activeColor);
        isDrawing = true;
        lastX = pos[0];
        lastY = pos[1];
        return true;
    }

    private boolean handleToolClick(double mx, double my) {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            int y = toolStartY + i * (TOOL_BTN_SIZE + TOOL_GAP);
            if (mx >= toolX && mx < toolX + TOOL_BTN_SIZE && my >= y && my < y + TOOL_BTN_SIZE) {
                currentTool = tools[i];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        isDrawing = false;
        lastX = lastY = -1;
        return super.mouseReleased(e);
    }

    private boolean handlePaletteClick(double mx, double my) {
        if (mx < palX || mx >= palX + 2 * FIXED_SWATCH_SIZE || my < palY || my >= palY + 8 * FIXED_SWATCH_SIZE) return false;
        int idx = ((int) ((my - palY) / FIXED_SWATCH_SIZE)) * 2 + (int) ((mx - palX) / FIXED_SWATCH_SIZE);
        if (idx < 0 || idx >= PALETTE.length) return true;

        int color = PALETTE[idx];
        if (!canUseColor(color)) {
            Item dye = COLOR_DYE_MAP.get(color);
            missingDyeMessage = "§c缺少" + new ItemStack(dye).getHoverName().getString();
            missingDyeMessageTicks = MISSING_DYE_MSG_DURATION;
            playLockSound();
            return true;
        }
        currentColor = color;
        return true;
    }

    private void onFinishDrawing() {
        BufferedImage img = new BufferedImage(GRID_SIZE, GRID_SIZE, BufferedImage.TYPE_INT_ARGB);
        boolean hasDrawn = false;

        for (int x = 0; x < GRID_SIZE; x++) for (int y = 0; y < GRID_SIZE; y++) {
            int color = pixels[x][y];
            img.setRGB(x, y, color == EMPTY_PIXEL ? 0x00000000 : color);
            if (color != EMPTY_PIXEL) hasDrawn = true;
        }

        if (!hasDrawn) {
            msg("§c你好像什么都没有画！");
            Minecraft.getInstance().setScreen(null);
            return;
        }

        if (!checkAndConsumeDyes()) return;

        Minecraft.getInstance().setScreen(null);
        msg("§e正在尝试理解你的画作...");

        PaintProcessor.recognizeImage(img, CANVAS_BG_COLOR);
    }

    private boolean checkAndConsumeDyes() {
        if (!isSurvivalMode()) return true;

        Set<Integer> usedColors = getUsedColorsInCanvas();
        if (usedColors.isEmpty()) return true;

        List<Integer> missing = new ArrayList<>();
        List<String> itemsToSendServer = new ArrayList<>();

        for (int color : usedColors) {
            if (countDyeForColor(color) < 1) {
                missing.add(color);
            } else {
                Item dye = COLOR_DYE_MAP.get(color);
                if (dye != null) {
                    itemsToSendServer.add(BuiltInRegistries.ITEM.getKey(dye).toString());
                }
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("§c提交失败，缺少染料：");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) sb.append("、");
                Item dye = COLOR_DYE_MAP.get(missing.get(i));
                sb.append(dye != null ? new ItemStack(dye).getHoverName().getString() : "未知染料");
            }
            msg(sb.toString());
            return false;
        }

        if (!itemsToSendServer.isEmpty() && Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(new ConsumeDyePayload(itemsToSendServer));
        }

        return true;
    }

    private boolean isSurvivalMode() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    private boolean hasAnyDye() {
        if (!isSurvivalMode()) return true;
        for (int color : PALETTE) {
            if (hasDyeForColor(color)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDyeForColor(int color) {
        return countDyeForColor(color) > 0;
    }

    private int countDyeForColor(int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Integer.MAX_VALUE;
        Item dye = COLOR_DYE_MAP.get(color);
        if (dye == null) return Integer.MAX_VALUE;

        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(dye)) total += stack.getCount();
        }
        return total;
    }

    private boolean canUseColor(int color) {
        return !isSurvivalMode() || hasDyeForColor(color);
    }

    private Integer getFirstAvailableColor() {
        for (int color : PALETTE) if (canUseColor(color)) return color;
        return null;
    }

    private Set<Integer> getUsedColorsInCanvas() {
        Set<Integer> used = new HashSet<>();
        for (int x = 0; x < GRID_SIZE; x++) for (int y = 0; y < GRID_SIZE; y++)
            if (pixels[x][y] != EMPTY_PIXEL) used.add(pixels[x][y]);
        return used;
    }

    private void floodFillRaw(int x, int y, int target, int replacement) {
        if (!inBounds(x, y) || target == replacement || pixels[x][y] != target) return;

        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{x, y});
        setCanvasPixel(x, y, replacement);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (inBounds(nx, ny) && pixels[nx][ny] == target) {
                    setCanvasPixel(nx, ny, replacement);
                    q.add(new int[]{nx, ny});
                }
            }
        }
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        if (x0 < 0 || y0 < 0) return;
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            if (inBounds(x0, y0)) setCanvasPixel(x0, y0, activeColor == EMPTY_PIXEL ? EMPTY_PIXEL : activeColor);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
    }

    private void drawOutline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private int[] getPixel(double mx, double my) {
        return mx < startX || my < startY || mx >= startX + canvasSize || my >= startY + canvasSize
                ? null : new int[]{(int) ((mx - startX) / pSize), (int) ((my - startY) / pSize)};
    }

    private void clearCanvas() {
        for (int[] row : pixels) Arrays.fill(row, EMPTY_PIXEL);
        if (this.canvasImage != null) {
            refreshEntireTexture();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // Ctrl+Z: 撤销
        if (keyEvent.key() == 90 && (keyEvent.modifiers() & 2) != 0) {
            undo();
            return true;
        }
        // Ctrl+Y: 重做
        if (keyEvent.key() == 89 && (keyEvent.modifiers() & 2) != 0) {
            redo();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    /** 在修改像素之前调用，保存当前画布状态到撤销栈。 */
    private void saveUndoState() {
        redoStack.clear();
        if (undoStack.size() >= MAX_UNDO_HISTORY) {
            undoStack.pollLast(); // 移除最旧的状态
        }
        undoStack.push(createSnapshot());
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(createSnapshot());
        restoreFromSnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(createSnapshot());
        restoreFromSnapshot(redoStack.pop());
    }

    private int[][] createSnapshot() {
        int[][] snapshot = new int[GRID_SIZE][GRID_SIZE];
        for (int x = 0; x < GRID_SIZE; x++) {
            snapshot[x] = pixels[x].clone();
        }
        return snapshot;
    }

    private void restoreFromSnapshot(int[][] snapshot) {
        for (int x = 0; x < GRID_SIZE; x++) {
            System.arraycopy(snapshot[x], 0, pixels[x], 0, GRID_SIZE);
        }
        refreshEntireTexture();
    }

    private void msg(String s) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(s), false);
    }

    private void msgBar(String s) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(s), true);
    }

    /**
     * 在画布底部渲染缺失染料提示，细条状态栏
     */
    private void renderMissingDyeMessage(GuiGraphics g) {
        if (missingDyeMessageTicks <= 0 || missingDyeMessage == null) return;

        int alpha;
        if (missingDyeMessageTicks > 90) {
            alpha = (int) ((MISSING_DYE_MSG_DURATION - missingDyeMessageTicks) / 10.0F * 255);
        } else if (missingDyeMessageTicks > 10) {
            alpha = 255;
        } else {
            alpha = (int) (missingDyeMessageTicks / 10.0F * 255);
        }
        alpha = Math.max(0, Math.min(255, alpha));

        String plainText = missingDyeMessage.replaceAll("§[0-9a-fk-or]", "");
        int textWidth = font.width(plainText);
        int barH = font.lineHeight + 6;
        int barY = startY + canvasSize - barH;
        int textX = startX + (canvasSize - textWidth) / 2;
        int textY = barY + 3;

        // 底部细条
        int bgAlpha = Math.min(alpha, 200);
        g.fill(startX, barY, startX + canvasSize, barY + barH, (bgAlpha << 24) | 0x000000);

        // 文字
        int textColor = (alpha << 24) | 0xFF6666;
        g.drawString(font, plainText, textX, textY, textColor);

        missingDyeMessageTicks--;
    }

    /**
     * 渲染操作说明按钮（"?"）
     */
    private void renderHelpButton(GuiGraphics g, int mx, int my) {
        boolean hover = isMouseOverHelpButton(mx, my);

        int bg = hover ? 0xAA3B6085 : 0xAA333333;
        int border = hover ? 0xFFFFAA00 : 0xFF666666;

        g.fill(helpBtnX, helpBtnY, helpBtnX + HELP_BTN_SIZE, helpBtnY + HELP_BTN_SIZE, bg);
        drawOutline(g, helpBtnX, helpBtnY, HELP_BTN_SIZE, HELP_BTN_SIZE, border);

        String questionMark = "?";
        int textWidth = font.width(questionMark);
        int textX = helpBtnX + (HELP_BTN_SIZE - textWidth) / 2;
        int textY = helpBtnY + (HELP_BTN_SIZE - font.lineHeight) / 2 + 1;
        int textColor = hover ? 0xFFFFFFFF : 0xFFAAAAAA;
        g.drawString(font, questionMark, textX, textY, textColor);
    }

    /**
     * 检测鼠标是否悬停在帮助按钮上
     */
    private boolean isMouseOverHelpButton(double mx, double my) {
        return mx >= helpBtnX && mx < helpBtnX + HELP_BTN_SIZE
                && my >= helpBtnY && my < helpBtnY + HELP_BTN_SIZE;
    }

    /**
     * 获取操作说明 tooltip
     */
    private List<Component> getHelpTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal("§e§l操作说明"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§fCtrl + Z : §7撤销"));
        tooltip.add(Component.literal("§fCtrl + Y : §7重做"));
        tooltip.add(Component.literal("§f工具切换: §7画布右侧面板"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7颜料§f低语§7时，"));
        tooltip.add(Component.literal("§7需要先画出颜料的需求。"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§7背包中携带§f书与笔§7时，"));
        tooltip.add(Component.literal("§7AI分析过程会自动写入其中。"));
        return tooltip;
    }

    /**
     * 播放锁定音效（低沉的按钮声）
     */
    private void playLockSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,
                            0.4F
                    )
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
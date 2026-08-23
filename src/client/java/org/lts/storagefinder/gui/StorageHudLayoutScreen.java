package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.StorageFinderConfig;

public final class StorageHudLayoutScreen extends Screen {
    private static final int MARGIN = 6;
    private final Screen parent;
    private final StorageFinderConfig config;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public StorageHudLayoutScreen(Screen parent, StorageFinderConfig config) {
        super(Component.translatable("storagefinder.hud_layout.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_layout.done"), button -> closeScreen())
                .bounds(width / 2 - 80, height - 28, 160, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Design System 2.0 — semi-transparent dark overlay (drag layout mode)
        graphics.fill(0, 0, width, height, 0xD00D141F);
        graphics.fill(0, 0, width, 24, 0xFF182638);
        graphics.fill(0, 23, width, 24, 0xFF2E435E);
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("storagefinder.hud_layout.description"),
                width / 2, 18, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int panelWidth = panelWidth();
        int panelHeight = 44;
        int x = panelX(panelWidth);
        int y = panelY(panelHeight);
        int alpha = Math.round(config.hudOpacityPercent * 255.0F / 100.0F);
        int background = alpha << 24 | 0x0012161D;
        graphics.fill(x, y, x + panelWidth, y + 20, background);
        graphics.fill(x, y, x + 3, y + 20, 0xFF40C4FF);
        graphics.text(font, Component.translatable("storagefinder.hud_layout.sample_item"),
                x + 8, y + 6, 0xFFF2F5F8, true);
        graphics.text(font, Component.translatable("storagefinder.hud_layout.sample_status"),
                x + panelWidth - 78, y + 6, 0xFFB5BEC9, true);
        graphics.fill(x, y + 22, x + panelWidth, y + panelHeight, background);
        graphics.text(font, Component.translatable("storagefinder.hud_layout.sample_details"),
                x + 8, y + 29, 0xFF69F0AE, true);
        graphics.outline(x, y, panelWidth, panelHeight, dragging ? 0xFFFFFFFF : 0xFF70C7FF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int panelWidth = panelWidth();
        int panelHeight = 44;
        int x = panelX(panelWidth);
        int y = panelY(panelHeight);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.x() >= x && event.x() < x + panelWidth
                && event.y() >= y && event.y() < y + panelHeight) {
            dragging = true;
            dragOffsetX = event.x() - x;
            dragOffsetY = event.y() - y;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) {
            return super.mouseDragged(event, deltaX, deltaY);
        }
        int panelWidth = panelWidth();
        int panelHeight = 44;
        int x = clamp((int) Math.round(event.x() - dragOffsetX), 0, width - panelWidth);
        int y = clamp((int) Math.round(event.y() - dragOffsetY), 38, height - 34 - panelHeight);
        boolean right = x + panelWidth / 2 >= width / 2;
        boolean bottom = y + panelHeight / 2 >= height / 2;
        config.hudAnchor = (bottom ? "BOTTOM_" : "TOP_") + (right ? "RIGHT" : "LEFT");
        config.hudOffsetX = clamp(right ? width - MARGIN - panelWidth - x : x - MARGIN, 0, 200);
        config.hudOffsetY = clamp(bottom ? height - MARGIN - panelHeight - y : y - MARGIN, 0, 200);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private int panelWidth() {
        return config.hudCompact ? 190 : 238;
    }

    private int panelX(int panelWidth) {
        boolean right = config.hudAnchor.endsWith("RIGHT");
        return clamp(right ? width - MARGIN - config.hudOffsetX - panelWidth
                : MARGIN + config.hudOffsetX, 0, width - panelWidth);
    }

    private int panelY(int panelHeight) {
        boolean bottom = config.hudAnchor.startsWith("BOTTOM");
        return clamp(bottom ? height - MARGIN - config.hudOffsetY - panelHeight
                : MARGIN + config.hudOffsetY, 38, height - 34 - panelHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.StorageFinderConfig;
import org.lts.storagefinder.StorageHudRenderer;

public final class StorageHudLayoutScreen extends Screen {
    private static final int NUDGE = 1;
    private static final int FAST_NUDGE = 8;
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
        int buttonWidth = 100;
        int gap = 8;
        int left = width / 2 - buttonWidth - gap / 2;
        int y = height / 2 + 14;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_layout.reset"), button -> {
            StorageFinderConfig defaults = StorageFinderConfig.defaultsCopy();
            config.hudAnchor = defaults.hudAnchor;
            config.hudOffsetX = defaults.hudOffsetX;
            config.hudOffsetY = defaults.hudOffsetY;
        }).bounds(left, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_layout.done"), button -> closeScreen())
                .bounds(left + buttonWidth + gap, y, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, UiTheme.OVERLAY);
        int centerY = height / 2;
        graphics.centeredText(font, title, width / 2, centerY - 30, UiTheme.TEXT);
        graphics.centeredText(font, Component.translatable("storagefinder.hud_layout.description"),
                width / 2, centerY - 16, UiTheme.MUTED);
        graphics.centeredText(font, Component.translatable("storagefinder.hud_layout.keyboard_hint"),
                width / 2, centerY - 4, UiTheme.MUTED);
        StorageHudRenderer.Bounds bounds = StorageHudRenderer.renderPreview(minecraft, graphics, config, width, height);
        boolean hovered = bounds.contains(mouseX, mouseY) && getChildAt(mouseX, mouseY).isEmpty();
        graphics.outline(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
                dragging ? UiTheme.TEXT : hovered ? UiTheme.FOCUS : UiTheme.ACCENT);

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (getChildAt(event.x(), event.y()).isPresent()) {
            return super.mouseClicked(event, doubleClick);
        }
        StorageHudRenderer.Bounds bounds = StorageHudRenderer.previewBounds(config, width, height);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && bounds.contains(event.x(), event.y())) {
            dragging = true;
            dragOffsetX = event.x() - bounds.x();
            dragOffsetY = event.y() - bounds.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging) {
            return super.mouseDragged(event, deltaX, deltaY);
        }
        StorageHudRenderer.Bounds bounds = StorageHudRenderer.previewBounds(config, width, height);
        moveTo((int) Math.round(event.x() - dragOffsetX), (int) Math.round(event.y() - dragOffsetY),
                bounds.width(), bounds.height());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        int step = event.hasShiftDown() ? FAST_NUDGE : NUDGE;
        int dx = switch (event.key()) {
            case InputConstants.KEY_LEFT -> -step;
            case InputConstants.KEY_RIGHT -> step;
            default -> 0;
        };
        int dy = switch (event.key()) {
            case InputConstants.KEY_UP -> -step;
            case InputConstants.KEY_DOWN -> step;
            default -> 0;
        };
        if (dx != 0 || dy != 0) {
            StorageHudRenderer.Bounds bounds = StorageHudRenderer.previewBounds(config, width, height);
            moveTo(bounds.x() + dx, bounds.y() + dy, bounds.width(), bounds.height());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private void moveTo(int x, int y, int panelWidth, int panelHeight) {
        int clampedX = clamp(x, 0, Math.max(0, width - panelWidth));
        int clampedY = clamp(y, 0, Math.max(0, height - panelHeight));
        boolean right = clampedX + panelWidth / 2 >= width / 2;
        boolean bottom = clampedY + panelHeight / 2 >= height / 2;
        int margin = StorageHudRenderer.MARGIN;
        config.hudAnchor = (bottom ? "BOTTOM_" : "TOP_") + (right ? "RIGHT" : "LEFT");
        config.hudOffsetX = clamp(right ? width - margin - panelWidth - clampedX : clampedX - margin,
                0, StorageFinderConfig.MAX_HUD_OFFSET);
        config.hudOffsetY = clamp(bottom ? height - margin - panelHeight - clampedY : clampedY - margin,
                0, StorageFinderConfig.MAX_HUD_OFFSET);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

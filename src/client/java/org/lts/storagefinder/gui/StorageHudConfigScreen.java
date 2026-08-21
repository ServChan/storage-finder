package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.StorageFinderConfig;

public final class StorageHudConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private final StorageFinderConfig config;

    public StorageHudConfigScreen(Screen parent, StorageFinderConfig config) {
        super(Component.translatable("storagefinder.hud_config.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(520, width - 40);
        int left = (width - contentWidth) / 2;
        int gap = 8;
        int column = (contentWidth - gap) / 2;
        int y = 42;

        addToggle(left, y, column, "storagefinder.hud_config.compact", () -> config.hudCompact,
                value -> config.hudCompact = value);
        addToggle(left + column + gap, y, column, "storagefinder.hud_config.distance",
                () -> config.hudShowDistance, value -> config.hudShowDistance = value);
        y += 25;
        addToggle(left, y, column, "storagefinder.hud_config.freshness",
                () -> config.hudShowFreshness, value -> config.hudShowFreshness = value);
        addRenderableWidget(Button.builder(anchorLabel(), button -> {
            config.hudAnchor = switch (config.hudAnchor) {
                case "TOP_LEFT" -> "TOP_RIGHT";
                case "TOP_RIGHT" -> "BOTTOM_RIGHT";
                case "BOTTOM_RIGHT" -> "BOTTOM_LEFT";
                default -> "TOP_LEFT";
            };
            rebuildWidgets();
        }).bounds(left + column + gap, y, column, BUTTON_HEIGHT).build());
        y += 29;

        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.hud_config.rows", config.hudMaxVisible),
                () -> config.hudMaxVisible = Math.max(1, config.hudMaxVisible - 1),
                () -> config.hudMaxVisible = Math.min(12, config.hudMaxVisible + 1));
        y += 25;
        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.hud_config.opacity", config.hudOpacityPercent),
                () -> config.hudOpacityPercent = Math.max(30, config.hudOpacityPercent - 5),
                () -> config.hudOpacityPercent = Math.min(100, config.hudOpacityPercent + 5));
        y += 25;
        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.hud_config.offset_x", config.hudOffsetX),
                () -> config.hudOffsetX = Math.max(0, config.hudOffsetX - 8),
                () -> config.hudOffsetX = Math.min(200, config.hudOffsetX + 8));
        y += 25;
        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.hud_config.offset_y", config.hudOffsetY),
                () -> config.hudOffsetY = Math.max(0, config.hudOffsetY - 8),
                () -> config.hudOffsetY = Math.min(200, config.hudOffsetY + 8));

        int bottomWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.reset"), button -> {
            config.hudAnchor = "BOTTOM_RIGHT";
            config.hudOffsetX = 0;
            config.hudOffsetY = 48;
            config.hudCompact = false;
            config.hudShowDistance = true;
            config.hudShowFreshness = true;
            config.hudMaxVisible = 6;
            config.hudOpacityPercent = 72;
            rebuildWidgets();
        }).bounds(left, height - 30, bottomWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.drag"), button ->
                minecraft.setScreenAndShow(new StorageHudLayoutScreen(this, config)))
                .bounds(left + bottomWidth + gap, height - 30, bottomWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.back"), button -> closeScreen())
                .bounds(left + (bottomWidth + gap) * 2, height - 30, bottomWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF010141C);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("storagefinder.hud_config.description"),
                width / 2, 28, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
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

    private void addToggle(int x, int y, int width, String key, BooleanGetter getter, BooleanSetter setter) {
        addRenderableWidget(Button.builder(toggleLabel(key, getter.get()), button -> {
            setter.set(!getter.get());
            button.setMessage(toggleLabel(key, getter.get()));
        }).bounds(x, y, width, BUTTON_HEIGHT).build());
    }

    private void addStepper(int x, int y, int width, Component label, Runnable decrease, Runnable increase) {
        int small = 40;
        int gap = 8;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            decrease.run();
            rebuildWidgets();
        }).bounds(x, y, small, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(label, button -> { })
                .bounds(x + small + gap, y, width - small * 2 - gap * 2, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            increase.run();
            rebuildWidgets();
        }).bounds(x + width - small, y, small, BUTTON_HEIGHT).build());
    }

    private Component anchorLabel() {
        return Component.translatable("storagefinder.config.hud_anchor",
                Component.translatable("storagefinder.config.hud_anchor." + config.hudAnchor.toLowerCase()));
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(
                value ? "storagefinder.config.on" : "storagefinder.config.off"));
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }

    @FunctionalInterface
    private interface BooleanGetter { boolean get(); }

    @FunctionalInterface
    private interface BooleanSetter { void set(boolean value); }
}

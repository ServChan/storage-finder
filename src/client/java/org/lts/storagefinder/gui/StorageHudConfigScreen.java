package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.StorageFinderConfig;

import java.util.Locale;

public final class StorageHudConfigScreen extends Screen {
    private static final int OFFSET_STEP = 8;
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
        int gap = UiTheme.GAP;
        int column = (contentWidth - gap) / 2;
        int right = left + column + gap;
        int y = UiTheme.CONTENT_TOP;
        StorageFinderConfig defaults = StorageFinderConfig.defaultsCopy();

        addRenderableWidget(UiTheme.toggle(left, y, column, "storagefinder.hud_config.compact",
                () -> config.hudCompact, value -> config.hudCompact = value));
        addRenderableWidget(UiTheme.toggle(right, y, column, "storagefinder.hud_config.distance",
                () -> config.hudShowDistance, value -> config.hudShowDistance = value));
        y += 24;
        addRenderableWidget(UiTheme.toggle(left, y, column, "storagefinder.hud_config.freshness",
                () -> config.hudShowFreshness, value -> config.hudShowFreshness = value));
        addRenderableWidget(Button.builder(anchorLabel(), button -> {
            config.hudAnchor = switch (config.hudAnchor) {
                case "TOP_LEFT" -> "TOP_RIGHT";
                case "TOP_RIGHT" -> "BOTTOM_RIGHT";
                case "BOTTOM_RIGHT" -> "BOTTOM_LEFT";
                default -> "TOP_LEFT";
            };
            rebuildWidgets();
        }).bounds(right, y, column, UiTheme.BUTTON_HEIGHT).build());
        y += 28;

        UiTheme.stepper(left, y, contentWidth, Component.translatable(
                        "storagefinder.hud_config.rows", config.hudMaxVisible),
                () -> config.hudMaxVisible = Math.max(1, config.hudMaxVisible - 1),
                () -> config.hudMaxVisible = defaults.hudMaxVisible,
                () -> config.hudMaxVisible = Math.min(12, config.hudMaxVisible + 1),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        y += 24;
        UiTheme.stepper(left, y, contentWidth, Component.translatable(
                        "storagefinder.hud_config.opacity", config.hudOpacityPercent),
                () -> config.hudOpacityPercent = Math.max(30, config.hudOpacityPercent - 5),
                () -> config.hudOpacityPercent = defaults.hudOpacityPercent,
                () -> config.hudOpacityPercent = Math.min(100, config.hudOpacityPercent + 5),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        y += 24;
        UiTheme.stepper(left, y, contentWidth, Component.translatable(
                        "storagefinder.hud_config.offset_x", config.hudOffsetX),
                () -> config.hudOffsetX = Math.max(0, config.hudOffsetX - OFFSET_STEP),
                () -> config.hudOffsetX = defaults.hudOffsetX,
                () -> config.hudOffsetX = Math.min(StorageFinderConfig.MAX_HUD_OFFSET, config.hudOffsetX + OFFSET_STEP),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        y += 24;
        UiTheme.stepper(left, y, contentWidth, Component.translatable(
                        "storagefinder.hud_config.offset_y", config.hudOffsetY),
                () -> config.hudOffsetY = Math.max(0, config.hudOffsetY - OFFSET_STEP),
                () -> config.hudOffsetY = defaults.hudOffsetY,
                () -> config.hudOffsetY = Math.min(StorageFinderConfig.MAX_HUD_OFFSET, config.hudOffsetY + OFFSET_STEP),
                this::rebuildWidgets).forEach(this::addRenderableWidget);

        int bottomWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.reset"), button -> {
            config.hudAnchor = defaults.hudAnchor;
            config.hudOffsetX = defaults.hudOffsetX;
            config.hudOffsetY = defaults.hudOffsetY;
            config.hudCompact = defaults.hudCompact;
            config.hudShowDistance = defaults.hudShowDistance;
            config.hudShowFreshness = defaults.hudShowFreshness;
            config.hudMaxVisible = defaults.hudMaxVisible;
            config.hudOpacityPercent = defaults.hudOpacityPercent;
            rebuildWidgets();
        }).bounds(left, height - 30, bottomWidth, UiTheme.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.drag"), button ->
                        minecraft.setScreenAndShow(new StorageHudLayoutScreen(this, config)))
                .bounds(left + bottomWidth + gap, height - 30, bottomWidth, UiTheme.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.hud_config.back"), button -> closeScreen())
                .bounds(left + (bottomWidth + gap) * 2, height - 30, bottomWidth, UiTheme.BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        UiTheme.drawFrame(graphics, font, width, height, title,
                Component.translatable("storagefinder.hud_config.description"));
        graphics.centeredText(font, Component.translatable("storagefinder.config.sub_screen_hint"),
                width / 2, height - 43, UiTheme.MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private Component anchorLabel() {
        return Component.translatable("storagefinder.config.hud_anchor",
                Component.translatable("storagefinder.config.hud_anchor." + config.hudAnchor.toLowerCase(Locale.ROOT)));
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

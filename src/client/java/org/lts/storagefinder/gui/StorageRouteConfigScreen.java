package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.StorageFinderConfig;

public final class StorageRouteConfigScreen extends Screen {
    private static final int BUDGET_STEP = 200;
    private final Screen parent;
    private final StorageFinderConfig config;
    private int contentLeft;
    private int contentWidth;
    private int hintsY;

    public StorageRouteConfigScreen(Screen parent, StorageFinderConfig config) {
        super(Component.translatable("storagefinder.route_config.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(520, width - 40);
        contentLeft = (width - contentWidth) / 2;
        int gap = UiTheme.GAP;
        int column = (contentWidth - gap) / 2;
        int y = UiTheme.CONTENT_TOP;
        StorageFinderConfig defaults = StorageFinderConfig.defaultsCopy();

        addRenderableWidget(UiTheme.toggle(contentLeft, y, column, "storagefinder.route_config.swimming",
                () -> config.routeAllowSwimming, value -> config.routeAllowSwimming = value));
        addRenderableWidget(UiTheme.toggle(contentLeft + column + gap, y, column, "storagefinder.route_config.doors",
                () -> config.routeThroughDoors, value -> config.routeThroughDoors = value));
        y += 28;
        UiTheme.stepper(contentLeft, y, contentWidth, Component.translatable(
                        "storagefinder.route_config.routes", config.routeMaxRoutes),
                () -> config.routeMaxRoutes = Math.max(1, config.routeMaxRoutes - 1),
                () -> config.routeMaxRoutes = defaults.routeMaxRoutes,
                () -> config.routeMaxRoutes = Math.min(5, config.routeMaxRoutes + 1),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        y += 24;
        UiTheme.stepper(contentLeft, y, contentWidth, Component.translatable(
                        "storagefinder.route_config.budget", config.routeNodeBudget),
                () -> config.routeNodeBudget = Math.max(200, config.routeNodeBudget - BUDGET_STEP),
                () -> config.routeNodeBudget = defaults.routeNodeBudget,
                () -> config.routeNodeBudget = Math.min(2_000, config.routeNodeBudget + BUDGET_STEP),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        hintsY = y + 32;

        addRenderableWidget(Button.builder(Component.translatable("storagefinder.route_config.reset"), button -> {
            config.routeAllowSwimming = defaults.routeAllowSwimming;
            config.routeThroughDoors = defaults.routeThroughDoors;
            config.routeMaxRoutes = defaults.routeMaxRoutes;
            config.routeNodeBudget = defaults.routeNodeBudget;
            rebuildWidgets();
        }).bounds(contentLeft, height - 30, (contentWidth - gap) / 2, UiTheme.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.route_config.back"), button -> closeScreen())
                .bounds(contentLeft + (contentWidth + gap) / 2, height - 30,
                        (contentWidth - gap) / 2, UiTheme.BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        UiTheme.drawFrame(graphics, font, width, height, title,
                Component.translatable("storagefinder.route_config.description"));
        int y = hintsY;
        for (String key : new String[]{"storagefinder.route_config.geometry_hint",
                "storagefinder.route_config.movement_hint", "storagefinder.route_config.budget_hint"}) {
            graphics.textWithWordWrap(font, Component.translatable(key), contentLeft, y, contentWidth, UiTheme.MUTED);
            y += font.wordWrapHeight(Component.translatable(key), contentWidth) + 4;
        }
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

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

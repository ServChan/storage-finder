package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.StorageFinderConfig;

public final class StorageRouteConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private final StorageFinderConfig config;

    public StorageRouteConfigScreen(Screen parent, StorageFinderConfig config) {
        super(Component.translatable("storagefinder.route_config.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(520, width - 40);
        int left = (width - contentWidth) / 2;
        int y = 48;

        addRenderableWidget(Button.builder(toggleLabel("storagefinder.route_config.swimming",
                config.routeAllowSwimming), button -> {
            config.routeAllowSwimming = !config.routeAllowSwimming;
            button.setMessage(toggleLabel("storagefinder.route_config.swimming", config.routeAllowSwimming));
        }).bounds(left, y, contentWidth, BUTTON_HEIGHT).build());
        y += 30;
        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.route_config.routes", config.routeMaxRoutes),
                () -> config.routeMaxRoutes = Math.max(1, config.routeMaxRoutes - 1),
                () -> config.routeMaxRoutes = Math.min(5, config.routeMaxRoutes + 1));
        y += 30;
        addStepper(left, y, contentWidth, Component.translatable(
                "storagefinder.route_config.budget", config.routeNodeBudget),
                () -> config.routeNodeBudget = Math.max(200, config.routeNodeBudget - 200),
                () -> config.routeNodeBudget = Math.min(2_000, config.routeNodeBudget + 200));

        addRenderableWidget(Button.builder(Component.translatable("storagefinder.route_config.reset"), button -> {
            config.routeAllowSwimming = true;
            config.routeMaxRoutes = 3;
            config.routeNodeBudget = 800;
            rebuildWidgets();
        }).bounds(left, height - 30, (contentWidth - 8) / 2, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.route_config.back"), button -> closeScreen())
                .bounds(left + (contentWidth + 8) / 2, height - 30,
                        (contentWidth - 8) / 2, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Design System 2.0 — dark container background
        graphics.fill(0, 0, width, height, 0xF00D141F);

        // Header bar
        graphics.fill(0, 0, width, 28, 0xFF182638);
        graphics.fill(0, 27, width, 28, 0xFF2E435E);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);

        graphics.centeredText(font, Component.translatable("storagefinder.route_config.description"),
                width / 2, 21, 0xFF9AA4B2);
        graphics.centeredText(font, Component.translatable("storagefinder.route_config.geometry_hint"),
                width / 2, 146, 0xFF80CBC4);
        graphics.centeredText(font, Component.translatable("storagefinder.route_config.budget_hint"),
                width / 2, 160, 0xFF9AA4B2);
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

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(
                value ? "storagefinder.config.on" : "storagefinder.config.off")
                .withStyle(style -> style.withColor(value ? 0xFF55FF55 : 0xFFFF5555)));
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageFinderConfig;

public final class StorageFinderConfigScreen extends Screen {
    private static final SystemToast.SystemToastId SAVE_TOAST_ID = new SystemToast.SystemToastId();
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private StorageFinderConfig config;

    public StorageFinderConfigScreen(Screen parent) {
        super(Component.translatable("storagefinder.config.title"));
        this.parent = parent;
        this.config = StorageFinderConfig.currentCopy();
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(520, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int gap = 8;
        int columnWidth = (contentWidth - gap) / 2;
        int y = 42;

        addToggle(left, y, columnWidth, "storagefinder.config.enabled", () -> config.enabled,
                value -> config.enabled = value);
        addToggle(left + columnWidth + gap, y, columnWidth, "storagefinder.config.index_containers",
                () -> config.indexContainers, value -> config.indexContainers = value);
        y += 28;

        addToggle(left, y, columnWidth, "storagefinder.config.search_frames", () -> config.searchItemFrames,
                value -> config.searchItemFrames = value);
        addToggle(left + columnWidth + gap, y, columnWidth, "storagefinder.config.search_memory",
                () -> config.searchRememberedContents, value -> config.searchRememberedContents = value);
        y += 28;

        addToggle(left, y, columnWidth, "storagefinder.config.highlight", () -> config.highlightEnabled,
                value -> config.highlightEnabled = value);
        addToggle(left + columnWidth + gap, y, columnWidth, "storagefinder.config.route", () -> config.routeEnabled,
                value -> config.routeEnabled = value);
        y += 28;

        addToggle(left, y, columnWidth, "storagefinder.config.avoid_hazards", () -> config.avoidHazards,
                value -> config.avoidHazards = value);
        addToggle(left + columnWidth + gap, y, columnWidth, "storagefinder.config.messages", () -> config.showMessages,
                value -> config.showMessages = value);
        y += 38;

        int radiusButtonWidth = 40;
        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            config.searchRadiusChunks = Math.max(1, config.searchRadiusChunks - 1);
            refreshScreenWidgets();
        }).bounds(left, y, radiusButtonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(radiusLabel(), button -> {
            config.searchRadiusChunks = StorageFinderConfig.MAX_SEARCH_CHUNKS;
            refreshScreenWidgets();
        }).bounds(left + radiusButtonWidth + gap, y, contentWidth - radiusButtonWidth * 2 - gap * 2, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            config.searchRadiusChunks = Math.min(StorageFinderConfig.MAX_SEARCH_CHUNKS, config.searchRadiusChunks + 1);
            refreshScreenWidgets();
        }).bounds(left + contentWidth - radiusButtonWidth, y, radiusButtonWidth, BUTTON_HEIGHT).build());

        int bottomY = this.height - 30;
        int bottomButtonWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.defaults"), button -> {
            config = StorageFinderConfig.defaultsCopy();
            rebuildWidgets();
        }).bounds(left, bottomY, bottomButtonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.save"), button -> saveAndClose())
                .bounds(left + bottomButtonWidth + gap, bottomY, bottomButtonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.cancel"), button -> closeScreen())
                .bounds(left + (bottomButtonWidth + gap) * 2, bottomY, bottomButtonWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF010141C);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("storagefinder.config.radius_hint"),
                this.width / 2, 142, 0xFF9AA4B2);
        graphics.centeredText(this.font, Component.translatable("storagefinder.config.hotkey_hint"),
                this.width / 2, Math.min(this.height - 52, 188), 0xFF88C0D0);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_S && event.hasControlDown()) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void addToggle(int x, int y, int width, String key, BooleanGetter getter, BooleanSetter setter) {
        addRenderableWidget(Button.builder(toggleLabel(key, getter.get()), button -> {
            setter.set(!getter.get());
            button.setMessage(toggleLabel(key, getter.get()));
        }).bounds(x, y, width, BUTTON_HEIGHT).build());
    }

    private Component radiusLabel() {
        return Component.translatable("storagefinder.config.radius", config.searchRadiusChunks,
                config.searchRadiusChunks * 16);
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(
                value ? "storagefinder.config.on" : "storagefinder.config.off"));
    }

    private void saveAndClose() {
        if (StorageFinderConfig.save(config)) {
            StorageFinderClient.onConfigChanged();
            SystemToast.addOrUpdate(this.minecraft.getToastManager(), SAVE_TOAST_ID,
                    Component.translatable("storagefinder.config.saved.title"),
                    Component.translatable("storagefinder.config.saved.description"));
            closeScreen();
        } else {
            SystemToast.addOrUpdate(this.minecraft.getToastManager(), SAVE_TOAST_ID,
                    Component.translatable("storagefinder.config.save_failed.title"),
                    Component.translatable("storagefinder.config.save_failed.description"));
        }
    }

    private void closeScreen() {
        this.minecraft.setScreen(parent);
    }

    private void refreshScreenWidgets() {
        clearWidgets();
        init();
    }

    @FunctionalInterface
    private interface BooleanGetter {
        boolean get();
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}

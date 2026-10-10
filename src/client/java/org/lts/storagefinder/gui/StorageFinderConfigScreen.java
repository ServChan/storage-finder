package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.MinecraftUiAccess;
import org.lts.storagefinder.StorageFinderConfig;

import java.util.List;
import java.util.Locale;

public final class StorageFinderConfigScreen extends Screen {
    private static final SystemToast.SystemToastId SAVE_TOAST_ID = new SystemToast.SystemToastId();
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
        int gap = UiTheme.GAP;
        int columnWidth = (contentWidth - gap) / 2;
        int right = left + columnWidth + gap;
        int y = UiTheme.CONTENT_TOP;

        addRenderableWidget(UiTheme.toggle(left, y, columnWidth, "storagefinder.config.enabled",
                () -> config.enabled, value -> config.enabled = value));
        addRenderableWidget(UiTheme.toggle(right, y, columnWidth, "storagefinder.config.index_containers",
                () -> config.indexContainers, value -> config.indexContainers = value));
        y += 24;
        addRenderableWidget(UiTheme.toggle(left, y, columnWidth, "storagefinder.config.search_frames",
                () -> config.searchItemFrames, value -> config.searchItemFrames = value));
        addRenderableWidget(UiTheme.toggle(right, y, columnWidth, "storagefinder.config.search_memory",
                () -> config.searchRememberedContents, value -> config.searchRememberedContents = value));
        y += 24;
        addRenderableWidget(UiTheme.toggle(left, y, columnWidth, "storagefinder.config.highlight",
                () -> config.highlightEnabled, value -> config.highlightEnabled = value));
        addRenderableWidget(UiTheme.toggle(right, y, columnWidth, "storagefinder.config.route",
                () -> config.routeEnabled, value -> config.routeEnabled = value));
        y += 24;
        addRenderableWidget(UiTheme.toggle(left, y, columnWidth, "storagefinder.config.avoid_hazards",
                () -> config.avoidHazards, value -> config.avoidHazards = value));
        addRenderableWidget(UiTheme.toggle(right, y, columnWidth, "storagefinder.config.messages",
                () -> config.showMessages, value -> config.showMessages = value));
        y += 24;
        addRenderableWidget(UiTheme.toggle(left, y, columnWidth, "storagefinder.config.shulker_preview",
                () -> config.shulkerPreview, value -> config.shulkerPreview = value));
        addRenderableWidget(Button.builder(completionLabel(), button -> {
            List<String> modes = StorageFinderConfig.COMPLETION_MODES;
            config.searchCompletion = modes.get((modes.indexOf(config.searchCompletion) + 1) % modes.size());
            button.setMessage(completionLabel());
        }).bounds(right, y, columnWidth, UiTheme.BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.config.completion.tooltip"))).build());
        y += 28;

        StorageFinderConfig defaults = StorageFinderConfig.defaultsCopy();
        UiTheme.stepper(left, y, contentWidth, radiusLabel(),
                () -> config.searchRadiusChunks = Math.max(1, config.searchRadiusChunks - 1),
                () -> config.searchRadiusChunks = defaults.searchRadiusChunks,
                () -> config.searchRadiusChunks = Math.min(StorageFinderConfig.MAX_SEARCH_CHUNKS,
                        config.searchRadiusChunks + 1),
                this::rebuildWidgets).forEach(this::addRenderableWidget);
        y += 28;

        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.hud_settings"), button ->
                        this.minecraft.setScreenAndShow(new StorageHudConfigScreen(this, config)))
                .bounds(left, y, columnWidth, UiTheme.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.route_settings"), button ->
                        this.minecraft.setScreenAndShow(new StorageRouteConfigScreen(this, config)))
                .bounds(right, y, columnWidth, UiTheme.BUTTON_HEIGHT).build());

        int bottomY = this.height - 30;
        int bottomButtonWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.defaults"), button -> {
            config = StorageFinderConfig.defaultsCopy();
            rebuildWidgets();
        }).bounds(left, bottomY, bottomButtonWidth, UiTheme.BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.config.defaults.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.save"), button -> saveAndClose())
                .bounds(left + bottomButtonWidth + gap, bottomY, bottomButtonWidth, UiTheme.BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.config.cancel"), button -> closeScreen())
                .bounds(left + (bottomButtonWidth + gap) * 2, bottomY, bottomButtonWidth, UiTheme.BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.config.cancel.tooltip"))).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        UiTheme.drawFrame(graphics, this.font, this.width, this.height, this.title,
                Component.translatable("storagefinder.config.hotkey_hint"));
        if (hasUnsavedChanges()) {
            graphics.centeredText(this.font, Component.translatable("storagefinder.config.unsaved"),
                    this.width / 2, this.height - 43, UiTheme.WARNING);
        }
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            requestClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_S && event.hasControlDown()) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean hasUnsavedChanges() {
        return !config.sameAs(StorageFinderConfig.current());
    }

    private void requestClose() {
        if (!hasUnsavedChanges()) {
            closeScreen();
            return;
        }
        this.minecraft.setScreenAndShow(new ConfirmScreen(discard -> {
            if (discard) {
                closeScreen();
            } else {
                this.minecraft.setScreenAndShow(this);
            }
        }, Component.translatable("storagefinder.config.unsaved.title"),
                Component.translatable("storagefinder.config.unsaved.message"),
                Component.translatable("storagefinder.config.discard"),
                Component.translatable("storagefinder.config.keep_editing")));
    }

    private Component completionLabel() {
        return Component.translatable("storagefinder.config.completion", Component.translatable(
                "storagefinder.config.completion." + config.searchCompletion.toLowerCase(Locale.ROOT)));
    }

    private Component radiusLabel() {
        return Component.translatable("storagefinder.config.radius", config.searchRadiusChunks,
                config.searchRadiusChunks * 16);
    }

    private void saveAndClose() {
        if (StorageFinderConfig.save(config)) {
            StorageFinderClient.onConfigChanged();
            SystemToast.addOrUpdate(MinecraftUiAccess.getToastManager(this.minecraft), SAVE_TOAST_ID,
                    Component.translatable("storagefinder.config.saved.title"),
                    Component.translatable("storagefinder.config.saved.description"));
            closeScreen();
        } else {
            SystemToast.addOrUpdate(MinecraftUiAccess.getToastManager(this.minecraft), SAVE_TOAST_ID,
                    Component.translatable("storagefinder.config.save_failed.title"),
                    Component.translatable("storagefinder.config.save_failed.description"));
        }
    }

    private void closeScreen() {
        this.minecraft.setScreenAndShow(parent);
    }
}

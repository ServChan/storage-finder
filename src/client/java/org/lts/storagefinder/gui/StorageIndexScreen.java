package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.MinecraftUiAccess;
import org.lts.storagefinder.IndexFreshness;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageIndex;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class StorageIndexScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private EditBox searchBox;
    private List<StorageFinderClient.IndexRecordView> records = List.of();
    private List<StorageFinderClient.IndexRecordView> filtered = List.of();
    private String selectedKey;
    private boolean allScopes;
    private boolean confirmClear;
    private int page;
    private int rows;
    private int left;
    private int contentWidth;
    private Button scopeButton;
    private Button deleteButton;
    private Button clearButton;

    public StorageIndexScreen(Screen parent) {
        super(Component.translatable("storagefinder.index_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(560, width - 30);
        left = (width - contentWidth) / 2;
        rows = Math.max(3, Math.min(10, (height - 150) / ROW_HEIGHT));
        int gap = 6;

        scopeButton = addRenderableWidget(Button.builder(scopeLabel(), button -> {
            allScopes = !allScopes;
            selectedKey = null;
            confirmClear = false;
            reload();
            updateButtons();
        }).bounds(left, 38, (contentWidth - gap) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.export"), button -> {
            Path exported = StorageFinderClient.exportIndex();
            Component message = exported == null
                    ? Component.translatable("storagefinder.index_manager.export_failed")
                    : Component.translatable("storagefinder.index_manager.exported", exported.toString());
            MinecraftUiAccess.addClientSystemMessage(minecraft, message);
        }).bounds(left + (contentWidth + gap) / 2, 38, (contentWidth - gap) / 2, 20).build());

        searchBox = new EditBox(font, left, 62, contentWidth, 20,
                Component.translatable("storagefinder.index_manager.search"));
        searchBox.setHint(Component.translatable("storagefinder.index_manager.search_hint"));
        searchBox.setResponder(this::filter);
        addRenderableWidget(searchBox);

        int actionY = height - 54;
        int actionWidth = (contentWidth - gap) / 2;
        deleteButton = addRenderableWidget(Button.builder(
                Component.translatable("storagefinder.index_manager.delete"), button -> deleteSelected())
                .bounds(left, actionY, actionWidth, 20).build());
        clearButton = addRenderableWidget(Button.builder(clearLabel(), button -> clearCurrentScope())
                .bounds(left + actionWidth + gap, actionY, actionWidth, 20).build());

        int bottomY = height - 30;
        int navigationWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.previous"), button ->
                page = Math.max(0, page - 1)).bounds(left, bottomY, navigationWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.back"), button -> closeScreen())
                .bounds(left + navigationWidth + gap, bottomY, navigationWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.next"), button -> {
            int pages = Math.max(1, (filtered.size() + rows - 1) / rows);
            page = Math.min(pages - 1, page + 1);
        }).bounds(left + (navigationWidth + gap) * 2, bottomY, navigationWidth, 20).build());

        reload();
        setInitialFocus(searchBox);
        updateButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Design System 2.0 — dark container background
        graphics.fill(0, 0, width, height, 0xF00D141F);

        // Header bar
        graphics.fill(0, 0, width, 28, 0xFF182638);
        graphics.fill(0, 27, width, 28, 0xFF2E435E);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("storagefinder.index_manager.summary",
                filtered.size(), records.size()), width / 2, 21, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int start = page * rows;
        int end = Math.min(filtered.size(), start + rows);
        int listY = 86;
        long now = System.currentTimeMillis();
        for (int index = start; index < end; index++) {
            StorageFinderClient.IndexRecordView view = filtered.get(index);
            StorageIndex.RecordView record = view.record();
            int rowY = listY + (index - start) * ROW_HEIGHT;
            boolean selected = record.key().equals(selectedKey);
            graphics.fill(left, rowY, left + contentWidth, rowY + ROW_HEIGHT - 1,
                    selected ? 0xEE355A7A : (index & 1) == 0 ? 0xEE1B2430 : 0xEE202B38);
            if (selected) {
                graphics.outline(left, rowY, contentWidth, ROW_HEIGHT - 1, 0xFF70C7FF);
            }
            String position = record.pos().getX() + ", " + record.pos().getY() + ", " + record.pos().getZ();
            graphics.text(font, Component.literal(position), left + 6, rowY + 3, 0xFFF2F5F8, true);
            Component amount = Component.translatable("storagefinder.index_manager.amount",
                    record.itemTypes(), record.itemCount());
            graphics.text(font, amount, left + contentWidth - font.width(amount) - 6,
                    rowY + 3, 0xFFB5BEC9, true);

            Component details = allScopes
                    ? Component.translatable("storagefinder.index_manager.details_scope", record.scope(),
                    freshnessLabel(record.freshness()), presenceLabel(view.presence()), ageLabel(now - record.updatedAt()))
                    : Component.translatable("storagefinder.index_manager.details",
                    freshnessLabel(record.freshness()), presenceLabel(view.presence()), ageLabel(now - record.updatedAt()));
            String detailText = trimToWidth(details.getString(), contentWidth - 12);
            graphics.text(font, Component.literal(detailText), left + 6, rowY + 14,
                    freshnessColor(record.freshness()), false);
        }
        if (filtered.isEmpty()) {
            graphics.centeredText(font, Component.translatable("storagefinder.index_manager.empty"),
                    width / 2, listY + 10, 0xFFFFA0A0);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int listY = 86;
            if (event.x() >= left && event.x() < left + contentWidth && event.y() >= listY) {
                int local = (int) ((event.y() - listY) / ROW_HEIGHT);
                int index = page * rows + local;
                if (local >= 0 && local < rows && index < filtered.size()) {
                    selectedKey = filtered.get(index).record().key();
                    confirmClear = false;
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE && selectedKey != null) {
            deleteSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private void reload() {
        records = StorageFinderClient.indexRecords(allScopes);
        filter(searchBox == null ? "" : searchBox.getValue());
        if (selectedKey != null && records.stream().noneMatch(view -> view.record().key().equals(selectedKey))) {
            selectedKey = null;
        }
        if (scopeButton != null) {
            scopeButton.setMessage(scopeLabel());
        }
    }

    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filtered = records.stream().filter(view -> {
            StorageIndex.RecordView record = view.record();
            String coordinates = record.pos().getX() + " " + record.pos().getY() + " " + record.pos().getZ();
            return needle.isEmpty() || coordinates.contains(needle)
                    || record.scope().toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        page = 0;
    }

    private void deleteSelected() {
        if (selectedKey == null) {
            return;
        }
        boolean persisted = StorageFinderClient.removeIndexRecord(selectedKey);
        MinecraftUiAccess.addClientSystemMessage(minecraft, Component.translatable(persisted
                ? "storagefinder.index_manager.deleted"
                : "storagefinder.index_manager.delete_failed"));
        selectedKey = null;
        confirmClear = false;
        reload();
        updateButtons();
    }

    private void clearCurrentScope() {
        if (allScopes) {
            return;
        }
        if (!confirmClear) {
            confirmClear = true;
            updateButtons();
            return;
        }
        int removed = StorageFinderClient.clearCurrentScopeIndex();
        MinecraftUiAccess.addClientSystemMessage(minecraft, removed >= 0
                ? Component.translatable("storagefinder.index_manager.cleared", removed)
                : Component.translatable("storagefinder.index_manager.clear_failed", -removed));
        confirmClear = false;
        selectedKey = null;
        reload();
        updateButtons();
    }

    private void updateButtons() {
        if (deleteButton != null) {
            deleteButton.active = selectedKey != null;
        }
        if (clearButton != null) {
            clearButton.active = !allScopes && !records.isEmpty();
            clearButton.setMessage(clearLabel());
        }
    }

    private Component scopeLabel() {
        return Component.translatable(allScopes
                ? "storagefinder.index_manager.scope_all"
                : "storagefinder.index_manager.scope_current");
    }

    private Component clearLabel() {
        return Component.translatable(confirmClear
                ? "storagefinder.index_manager.clear_confirm"
                : "storagefinder.index_manager.clear_scope");
    }

    private static Component freshnessLabel(IndexFreshness freshness) {
        return Component.translatable("storagefinder.freshness." + freshness.name().toLowerCase(Locale.ROOT));
    }

    private static Component presenceLabel(StorageFinderClient.RecordPresence presence) {
        return Component.translatable("storagefinder.presence." + presence.name().toLowerCase(Locale.ROOT));
    }

    private static Component ageLabel(long ageMillis) {
        long minutes = Math.max(0L, ageMillis) / 60_000L;
        if (minutes < 60) {
            return Component.translatable("storagefinder.age.minutes", minutes);
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return Component.translatable("storagefinder.age.hours", hours);
        }
        return Component.translatable("storagefinder.age.days", hours / 24);
    }

    private static int freshnessColor(IndexFreshness freshness) {
        return switch (freshness) {
            case FRESH -> 0xFF69F0AE;
            case AGING -> 0xFFFFD740;
            case STALE -> 0xFFFF8A80;
            case FRAME_ONLY -> 0xFF80D8FF;
        };
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

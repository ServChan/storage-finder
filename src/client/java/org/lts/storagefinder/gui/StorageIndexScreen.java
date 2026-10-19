package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.MinecraftUiAccess;
import org.lts.storagefinder.IndexFreshness;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageIndex;

import java.util.List;
import java.util.Locale;

public final class StorageIndexScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = UiTheme.CONTENT_TOP + 48;
    private final Screen parent;
    private EditBox searchBox;
    private List<StorageFinderClient.IndexRecordView> records = List.of();
    private List<StorageFinderClient.IndexRecordView> filtered = List.of();
    private String selectedKey;
    private boolean allScopes;
    private boolean confirmClear;
    private boolean exporting;
    private int page;
    private int rows;
    private int left;
    private int contentWidth;
    private Button scopeButton;
    private Button exportButton;
    private Button routeButton;
    private Button deleteButton;
    private Button clearButton;
    private Button previousButton;
    private Button nextButton;

    public StorageIndexScreen(Screen parent) {
        super(Component.translatable("storagefinder.index_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String previousQuery = searchBox == null ? "" : searchBox.getValue();
        contentWidth = Math.min(560, width - 30);
        left = (width - contentWidth) / 2;
        rows = Math.max(3, Math.min(10, (height - LIST_TOP - 58 - 14) / ROW_HEIGHT));
        int gap = 6;
        int halfWidth = (contentWidth - gap) / 2;

        scopeButton = addRenderableWidget(Button.builder(scopeLabel(), button -> {
            allScopes = !allScopes;
            selectedKey = null;
            confirmClear = false;
            reload();
        }).bounds(left, UiTheme.CONTENT_TOP, halfWidth, 20).build());
        exportButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.export"),
                button -> export()).bounds(left + halfWidth + gap, UiTheme.CONTENT_TOP, halfWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.index_manager.export.tooltip"))).build());

        searchBox = new EditBox(font, left, UiTheme.CONTENT_TOP + 24, contentWidth, 20,
                Component.translatable("storagefinder.index_manager.search"));
        searchBox.setHint(Component.translatable("storagefinder.index_manager.search_hint"));
        searchBox.setValue(previousQuery);
        searchBox.setResponder(this::filter);
        addRenderableWidget(searchBox);

        int actionY = height - 54;
        int thirdWidth = (contentWidth - gap * 2) / 3;
        routeButton = addRenderableWidget(Button.builder(
                        Component.translatable("storagefinder.index_manager.route"), button -> routeToSelected())
                .bounds(left, actionY, thirdWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.index_manager.route.tooltip"))).build());
        deleteButton = addRenderableWidget(Button.builder(
                        Component.translatable("storagefinder.index_manager.delete"), button -> deleteSelected())
                .bounds(left + thirdWidth + gap, actionY, thirdWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.index_manager.delete.tooltip"))).build());
        clearButton = addRenderableWidget(Button.builder(clearLabel(), button -> clearCurrentScope())
                .bounds(left + (thirdWidth + gap) * 2, actionY, thirdWidth, 20).build());

        int bottomY = height - 30;
        int navigationWidth = (contentWidth - gap * 2) / 3;
        previousButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.previous"),
                button -> changePage(-1)).bounds(left, bottomY, navigationWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.back"), button -> closeScreen())
                .bounds(left + navigationWidth + gap, bottomY, navigationWidth, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.index_manager.next"),
                button -> changePage(1)).bounds(left + (navigationWidth + gap) * 2, bottomY, navigationWidth, 20).build());

        reload();
        setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        UiTheme.drawFrame(graphics, font, width, height, title, Component.translatable(
                "storagefinder.index_manager.summary", filtered.size(), records.size()));
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int hovered = rowAt(mouseX, mouseY);
        int start = page * rows;
        int end = Math.min(filtered.size(), start + rows);
        long now = System.currentTimeMillis();
        for (int index = start; index < end; index++) {
            StorageFinderClient.IndexRecordView view = filtered.get(index);
            StorageIndex.RecordView record = view.record();
            int rowY = LIST_TOP + (index - start) * ROW_HEIGHT;
            boolean selected = record.key().equals(selectedKey);
            graphics.fill(left, rowY, left + contentWidth, rowY + ROW_HEIGHT - 1,
                    selected ? UiTheme.ROW_SELECTED : index == hovered ? UiTheme.ROW_HOVER
                            : (index & 1) == 0 ? UiTheme.ROW : UiTheme.ROW_ALT);
            if (selected) {
                graphics.outline(left, rowY, contentWidth, ROW_HEIGHT - 1, UiTheme.ACCENT);
            }
            graphics.fill(left, rowY, left + 3, rowY + ROW_HEIGHT - 1, freshnessColor(record.freshness()));
            String position = record.pos().getX() + ", " + record.pos().getY() + ", " + record.pos().getZ();
            graphics.text(font, position, left + 8, rowY + 3, UiTheme.BODY, true);
            Component amount = Component.translatable("storagefinder.index_manager.amount",
                    record.itemTypes(), record.itemCount());
            graphics.text(font, amount, left + contentWidth - font.width(amount) - 6,
                    rowY + 3, UiTheme.MUTED, true);

            Component details = allScopes
                    ? Component.translatable("storagefinder.index_manager.details_scope", record.scope(),
                    freshnessLabel(record.freshness()), presenceLabel(view.presence()), ageLabel(now - record.updatedAt()))
                    : Component.translatable("storagefinder.index_manager.details",
                    freshnessLabel(record.freshness()), presenceLabel(view.presence()), ageLabel(now - record.updatedAt()));
            graphics.text(font, UiTheme.trim(font, details.getString(), contentWidth - 14), left + 8, rowY + 14,
                    freshnessColor(record.freshness()), false);
        }
        if (filtered.isEmpty()) {
            Component empty = records.isEmpty()
                    ? Component.translatable("storagefinder.index_manager.empty")
                    : Component.translatable("storagefinder.index_manager.no_matches");
            graphics.textWithWordWrap(font, empty, left, LIST_TOP + 6, contentWidth, UiTheme.MUTED);
        } else {
            graphics.centeredText(font, Component.translatable("storagefinder.index_manager.page",
                    page + 1, pageCount()), width / 2, LIST_TOP + rows * ROW_HEIGHT + 2, UiTheme.MUTED);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (getChildAt(event.x(), event.y()).isPresent()) {
            return super.mouseClicked(event, doubleClick);
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            String clicked = filtered.get(index).record().key();
            boolean again = clicked.equals(selectedKey);
            selectedKey = clicked;
            confirmClear = false;
            updateButtons();
            if (doubleClick && again) {
                routeToSelected();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && !filtered.isEmpty()) {
            changePage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                && selectedKey != null && !searchBox.isFocused()) {
            routeToSelected();
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE && selectedKey != null && !isInputCaptured()) {
            deleteSelected();
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN) {
            changePage(1);
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEUP) {
            changePage(-1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private int rowAt(double x, double y) {
        if (x < left || x >= left + contentWidth || y < LIST_TOP) {
            return -1;
        }
        int local = (int) ((y - LIST_TOP) / ROW_HEIGHT);
        int index = page * rows + local;
        return local < rows && index < filtered.size() ? index : -1;
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + rows - 1) / rows);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        updateButtons();
    }

    private void reload() {
        records = StorageFinderClient.indexRecords(allScopes);
        filter(searchBox == null ? "" : searchBox.getValue());
        if (scopeButton != null) {
            scopeButton.setMessage(scopeLabel());
        }
    }

    private void filter(String query) {
        String needle = normalize(query);
        filtered = records.stream().filter(view -> {
            StorageIndex.RecordView record = view.record();
            String coordinates = record.pos().getX() + " " + record.pos().getY() + " " + record.pos().getZ();
            return needle.isEmpty() || coordinates.contains(needle)
                    || record.scope().toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        if (selectedKey != null && filtered.stream().noneMatch(view -> view.record().key().equals(selectedKey))) {
            selectedKey = null;
        }
        page = 0;
        updateButtons();
    }

    private static String normalize(String query) {
        return query.toLowerCase(Locale.ROOT).replace(',', ' ').replace(';', ' ')
                .strip().replaceAll("\\s+", " ");
    }

    private void export() {
        if (exporting) {
            return;
        }
        exporting = true;
        updateButtons();
        StorageFinderClient.exportIndex(exported -> {
            exporting = false;
            updateButtons();
            MinecraftUiAccess.addClientSystemMessage(minecraft, exported == null
                    ? Component.translatable("storagefinder.index_manager.export_failed")
                    : Component.translatable("storagefinder.index_manager.exported", exported.toString()));
        });
    }

    private StorageFinderClient.IndexRecordView selectedView() {
        if (selectedKey == null) {
            return null;
        }
        for (StorageFinderClient.IndexRecordView view : filtered) {
            if (view.record().key().equals(selectedKey)) {
                return view;
            }
        }
        return null;
    }

    private void routeToSelected() {
        StorageFinderClient.IndexRecordView view = selectedView();
        if (view == null || view.presence() == StorageFinderClient.RecordPresence.OTHER_SCOPE) {
            return;
        }
        StorageFinderClient.routeToRecord(view.record());
        minecraft.setScreenAndShow(null);
    }

    private void deleteSelected() {
        if (selectedKey == null) {
            return;
        }
        if (StorageFinderClient.removeIndexRecord(selectedKey)) {
            MinecraftUiAccess.addClientSystemMessage(minecraft,
                    Component.translatable("storagefinder.index_manager.deleted"));
        }
        selectedKey = null;
        confirmClear = false;
        reload();
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
        MinecraftUiAccess.addClientSystemMessage(minecraft,
                Component.translatable("storagefinder.index_manager.cleared", removed));
        confirmClear = false;
        selectedKey = null;
        reload();
    }

    private void updateButtons() {
        if (deleteButton != null) {
            deleteButton.active = selectedKey != null;
        }
        if (routeButton != null) {
            StorageFinderClient.IndexRecordView view = selectedView();
            routeButton.active = view != null && view.presence() != StorageFinderClient.RecordPresence.OTHER_SCOPE;
        }
        if (clearButton != null) {
            clearButton.active = !allScopes && !records.isEmpty();
            clearButton.setMessage(clearLabel());
        }
        if (exportButton != null) {
            exportButton.active = !exporting;
        }
        if (previousButton != null) {
            previousButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = page < pageCount() - 1;
        }
    }

    private Component scopeLabel() {
        return Component.translatable(allScopes
                ? "storagefinder.index_manager.scope_all"
                : "storagefinder.index_manager.scope_current");
    }

    private Component clearLabel() {
        return confirmClear
                ? Component.translatable("storagefinder.index_manager.clear_confirm")
                .withStyle(style -> style.withColor(UiTheme.DANGER))
                : Component.translatable("storagefinder.index_manager.clear_scope");
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
            case FRESH -> UiTheme.SUCCESS;
            case AGING -> UiTheme.WARNING;
            case STALE -> UiTheme.DANGER;
            case FRAME_ONLY -> UiTheme.INFO;
        };
    }

    private void closeScreen() {
        minecraft.setScreenAndShow(parent);
    }
}

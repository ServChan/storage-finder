package org.lts.storagefinder.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lts.storagefinder.ItemKeys;
import org.lts.storagefinder.SearchSelection;
import org.lts.storagefinder.StorageFinderClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class StorageSearchScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 12;
    private static final int LIST_TOP = UiTheme.CONTENT_TOP + 24;

    private EditBox searchBox;
    private List<Suggestion> catalog = List.of();
    private List<Suggestion> matches = List.of();
    private Map<String, Integer> knownCounts = Map.of();
    private int selected;
    private int scroll;
    private int fieldX;
    private int fieldWidth;
    private int visibleRows;

    public StorageSearchScreen() {
        super(Component.translatable("storagefinder.search.title"));
    }

    @Override
    protected void init() {
        String previousQuery = searchBox == null ? "" : searchBox.getValue();
        fieldWidth = Math.min(440, this.width - 40);
        fieldX = (this.width - fieldWidth) / 2;
        int listBottom = this.height - 30 - 18;
        visibleRows = Math.max(3, Math.min(MAX_VISIBLE_ROWS, (listBottom - LIST_TOP) / ROW_HEIGHT));

        searchBox = new EditBox(this.font, fieldX, UiTheme.CONTENT_TOP, fieldWidth, 20,
                Component.translatable("storagefinder.search.input"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("storagefinder.search.hint"));
        searchBox.setValue(previousQuery);
        searchBox.setResponder(this::updateSuggestions);
        addRenderableWidget(searchBox);

        int buttonY = this.height - 30;
        int gap = UiTheme.GAP;
        int buttonWidth = (fieldWidth - gap * 3) / 4;
        Button clear = addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.clear"), button -> {
            StorageFinderClient.clearSearch();
            closeScreen();
        }).bounds(fieldX, buttonY, buttonWidth, 20).build());
        clear.active = !StorageFinderClient.activeQueries().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.statistics"), button ->
                        this.minecraft.setScreenAndShow(new StorageStatisticsScreen()))
                .bounds(fieldX + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.index"), button ->
                        this.minecraft.setScreenAndShow(new StorageIndexScreen(this)))
                .bounds(fieldX + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.cancel"), button -> closeScreen())
                .bounds(fieldX + (buttonWidth + gap) * 3, buttonY, buttonWidth, 20).build());

        knownCounts = StorageFinderClient.allKnownItemStatistics();
        catalog = buildCatalog();
        updateSuggestions(previousQuery);
        setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        UiTheme.drawFrame(graphics, this.font, this.width, this.height, this.title,
                Component.translatable("storagefinder.search.description"));
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int hovered = rowAt(mouseX, mouseY);
        int end = Math.min(matches.size(), scroll + visibleRows);
        for (int index = scroll; index < end; index++) {
            Suggestion suggestion = matches.get(index);
            int rowY = LIST_TOP + (index - scroll) * ROW_HEIGHT;
            Integer searchColor = colorOf(suggestion);
            boolean isSelected = index == selected;
            int background = isSelected ? UiTheme.ROW_SELECTED
                    : index == hovered ? UiTheme.ROW_HOVER
                    : searchColor != null ? UiTheme.ROW_ACTIVE : UiTheme.ROW;
            graphics.fill(fieldX, rowY, fieldX + fieldWidth, rowY + ROW_HEIGHT - 1, background);
            graphics.outline(fieldX, rowY, fieldWidth, ROW_HEIGHT - 1,
                    isSelected ? UiTheme.ACCENT : searchColor != null ? searchColor : UiTheme.ROW_BORDER);
            graphics.item(suggestion.stack(), fieldX + 3, rowY + 2);

            int count = suggestion.knownCount();
            String countText = count > 0 ? Component.translatable("storagefinder.search.known_count", count).getString() : "";
            int countWidth = countText.isEmpty() ? 0 : this.font.width(countText) + 8;
            Component label = searchColor == null ? suggestion.label()
                    : suggestion.group() == null && StorageFinderClient.isPinned(suggestion.stack())
                    ? Component.translatable("storagefinder.search.pinned", suggestion.label())
                    : Component.translatable("storagefinder.search.active", suggestion.label());
            graphics.text(this.font, UiTheme.trim(this.font, label.getString(), fieldWidth - 30 - countWidth),
                    fieldX + 24, rowY + 7, suggestion.group() == null ? UiTheme.TEXT : UiTheme.ACCENT, false);
            if (!countText.isEmpty()) {
                graphics.text(this.font, countText, fieldX + fieldWidth - 6 - this.font.width(countText),
                        rowY + 7, UiTheme.SUCCESS, false);
            }
        }
        UiTheme.drawScrollbar(graphics, fieldX + fieldWidth + 3, LIST_TOP,
                visibleRows * ROW_HEIGHT - 1, matches.size(), visibleRows, scroll);

        int footerY = LIST_TOP + visibleRows * ROW_HEIGHT + 4;
        if (matches.isEmpty()) {
            String typed = searchBox.getValue().strip();
            Component empty = typed.isEmpty()
                    ? Component.translatable("storagefinder.search.empty_catalog")
                    : typed.startsWith("#")
                    ? Component.translatable("storagefinder.search.tag_unknown", typed)
                    : Component.translatable("storagefinder.search.no_results", typed);
            graphics.textWithWordWrap(this.font, empty, fieldX, LIST_TOP + 6, fieldWidth, UiTheme.MUTED);
        } else {
            Component footer = Component.translatable("storagefinder.search.footer", matches.size());
            graphics.centeredText(this.font, UiTheme.trim(this.font, footer.getString(), fieldWidth),
                    this.width / 2, footerY, UiTheme.MUTED);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (getChildAt(event.x(), event.y()).isPresent()) {
            return super.mouseClicked(event, doubleClick);
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            choose(matches.get(index));
            return true;
        }
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                && matches.get(index).group() == null) {
            togglePin(matches.get(index));
            selected = index;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!matches.isEmpty() && scrollY != 0.0) {
            scrollTo(scroll - (int) Math.signum(scrollY) * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> {
                closeScreen();
                return true;
            }
            case InputConstants.KEY_DOWN -> {
                return moveSelection(1);
            }
            case InputConstants.KEY_UP -> {
                return moveSelection(-1);
            }
            case InputConstants.KEY_PAGEDOWN -> {
                return moveSelection(visibleRows);
            }
            case InputConstants.KEY_PAGEUP -> {
                return moveSelection(-visibleRows);
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (!matches.isEmpty()) {
                    if (event.hasShiftDown() && matches.get(selected).group() == null) {
                        togglePin(matches.get(selected));
                    } else {
                        choose(matches.get(selected));
                    }
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private int rowAt(double x, double y) {
        if (x < fieldX || x >= fieldX + fieldWidth || y < LIST_TOP) {
            return -1;
        }
        int local = (int) ((y - LIST_TOP) / ROW_HEIGHT);
        int index = scroll + local;
        return local < visibleRows && index < matches.size() ? index : -1;
    }

    private boolean moveSelection(int delta) {
        if (matches.isEmpty()) {
            return false;
        }
        selected = Math.max(0, Math.min(matches.size() - 1, selected + delta));
        if (selected < scroll) {
            scrollTo(selected);
        } else if (selected >= scroll + visibleRows) {
            scrollTo(selected - visibleRows + 1);
        }
        return true;
    }

    private void scrollTo(int value) {
        scroll = Math.max(0, Math.min(Math.max(0, matches.size() - visibleRows), value));
    }

    private List<Suggestion> buildCatalog() {
        List<Suggestion> suggestions = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == Items.ENCHANTED_BOOK) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            suggestions.add(suggestion(stack.getHoverName(), stack,
                    BuiltInRegistries.ITEM.getKey(item).toString()));
        }

        if (this.minecraft.level != null) {
            this.minecraft.level.registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(registry ->
                    registry.entrySet().forEach(entry -> {
                        Component label = Component.translatable("storagefinder.search.enchanted_book",
                                entry.getValue().description());
                        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
                        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                        enchantments.set(registry.wrapAsHolder(entry.getValue()), 1);
                        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
                        suggestions.add(suggestion(label, stack, entry.getKey().identifier().toString()));
                    }));
        }

        Map<String, Long> labelCounts = suggestions.stream().collect(Collectors.groupingBy(
                Suggestion::labelText, Collectors.counting()));
        return suggestions.stream().map(suggestion -> {
            if (labelCounts.getOrDefault(suggestion.labelText(), 0L) <= 1L) {
                return suggestion;
            }
            Component label = Component.translatable("storagefinder.search.duplicate",
                    suggestion.label(), suggestion.id());
            return suggestion(label, suggestion.stack(), suggestion.id());
        }).toList();
    }

    private Suggestion groupSuggestion(String needle, List<Suggestion> itemMatches) {
        if (needle.startsWith("#")) {
            String tagName = needle.substring(1);
            Identifier id = Identifier.tryParse(tagName.contains(":") ? tagName : "minecraft:" + tagName);
            if (id == null) {
                return null;
            }
            Set<String> keys = new LinkedHashSet<>();
            ItemStack icon = ItemStack.EMPTY;
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
                ItemStack stack = new ItemStack(holder.value());
                if (icon.isEmpty()) {
                    icon = stack;
                }
                keys.addAll(SearchSelection.itemIds(stack));
            }
            if (keys.isEmpty()) {
                return null;
            }
            String groupId = "#" + id;
            Component label = Component.translatable("storagefinder.search.group_tag", groupId, keys.size());
            return groupRow(label, icon, groupId, keys, false, Component.literal(groupId));
        }
        if (needle.length() < 2) {
            return null;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Suggestion suggestion : itemMatches) {
            keys.addAll(SearchSelection.itemIds(suggestion.stack()));
        }
        ItemStack icon = itemMatches.isEmpty() ? new ItemStack(Items.NAME_TAG) : itemMatches.getFirst().stack();
        Component label = Component.translatable("storagefinder.search.group", needle, itemMatches.size());
        return groupRow(label, icon, needle, keys, true,
                Component.translatable("storagefinder.search.group_name", needle));
    }

    private Suggestion groupRow(Component label, ItemStack icon, String groupId, Set<String> keys,
                                boolean customNames, Component queryName) {
        int known = 0;
        for (String key : keys) {
            known = ItemKeys.addCounts(known, knownCounts.getOrDefault(key, 0));
        }
        String labelText = label.getString();
        return new Suggestion(label, labelText, labelText.toLowerCase(Locale.ROOT), groupId, icon, groupId, known,
                new Group(groupId, Set.copyOf(keys), customNames, queryName));
    }

    private Integer colorOf(Suggestion suggestion) {
        return suggestion.group() == null
                ? StorageFinderClient.searchColor(suggestion.stack())
                : StorageFinderClient.groupColor(suggestion.group().id());
    }

    private Suggestion suggestion(Component label, ItemStack stack, String id) {
        Set<String> keys = SearchSelection.itemIds(stack);
        int known = 0;
        for (String key : keys) {
            known = Math.max(known, knownCounts.getOrDefault(key, 0));
        }
        String labelText = label.getString();
        return new Suggestion(label, labelText, labelText.toLowerCase(Locale.ROOT),
                id.toLowerCase(Locale.ROOT), stack, id, known, null);
    }

    private void updateSuggestions(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        Comparator<Suggestion> order = Comparator
                .comparingInt((Suggestion suggestion) ->
                        StorageFinderClient.searchColor(suggestion.stack()) == null ? 1 : 0)
                .thenComparingInt(suggestion -> matchRank(suggestion, needle))
                .thenComparingInt(suggestion -> suggestion.knownCount() > 0 ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Suggestion::knownCount).reversed())
                .thenComparing(Suggestion::labelText, String.CASE_INSENSITIVE_ORDER);
        List<Suggestion> itemMatches = catalog.stream()
                .filter(suggestion -> needle.isEmpty()
                        ? suggestion.knownCount() > 0 || StorageFinderClient.searchColor(suggestion.stack()) != null
                        : suggestion.lowerLabel().contains(needle) || suggestion.lowerId().contains(needle))
                .sorted(order)
                .toList();
        Suggestion group = needle.isEmpty() ? null : groupSuggestion(needle, itemMatches);
        if (group == null) {
            matches = itemMatches;
            selected = 0;
        } else {
            List<Suggestion> combined = new ArrayList<>(itemMatches.size() + 1);
            combined.add(group);
            combined.addAll(itemMatches);
            matches = List.copyOf(combined);
            selected = itemMatches.isEmpty() ? 0 : 1;
        }
        scroll = 0;
    }

    private static int matchRank(Suggestion suggestion, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        String label = suggestion.lowerLabel();
        String path = suggestion.lowerId().substring(suggestion.lowerId().indexOf(':') + 1);
        if (label.equals(needle) || path.equals(needle) || suggestion.lowerId().equals(needle)) {
            return 0;
        }
        if (label.startsWith(needle) || path.startsWith(needle)) {
            return 1;
        }
        return label.contains(" " + needle) || path.contains("_" + needle) ? 2 : 3;
    }

    private void togglePin(Suggestion suggestion) {
        if (StorageFinderClient.searchColor(suggestion.stack()) == null) {
            StorageFinderClient.selectFromSearch(suggestion.stack(), suggestion.label());
        }
        StorageFinderClient.togglePinned(suggestion.stack());
    }

    private void choose(Suggestion suggestion) {
        if (suggestion.group() != null) {
            Group group = suggestion.group();
            StorageFinderClient.selectGroup(group.id(), suggestion.stack(), group.queryName(), group.keys(),
                    group.customNames());
        } else {
            StorageFinderClient.selectFromSearch(suggestion.stack(), suggestion.label());
        }
        closeScreen();
    }

    private void closeScreen() {
        this.minecraft.setScreenAndShow(null);
    }

    private record Suggestion(Component label, String labelText, String lowerLabel, String lowerId,
                              ItemStack stack, String id, int knownCount, Group group) {
    }

    private record Group(String id, Set<String> keys, boolean customNames, Component queryName) {
    }
}

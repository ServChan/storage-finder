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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageFinderConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StorageStatisticsScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = UiTheme.CONTENT_TOP + 48;
    private static final String ENCHANTMENT_MARKER = "|stored_enchantment=";
    private EditBox searchBox;
    private Button nearbyButton;
    private Button everywhereButton;
    private Button previousButton;
    private Button nextButton;
    private List<Entry> allEntries = List.of();
    private List<Entry> filteredEntries = List.of();
    private int page;
    private int rows;
    private int left;
    private int contentWidth;
    private boolean nearby = true;

    public StorageStatisticsScreen() {
        super(Component.translatable("storagefinder.statistics.title"));
    }

    @Override
    protected void init() {
        String previousQuery = searchBox == null ? "" : searchBox.getValue();
        contentWidth = Math.min(440, width - 40);
        left = (width - contentWidth) / 2;
        rows = Math.max(3, Math.min(12, (height - LIST_TOP - 30 - 18) / ROW_HEIGHT));
        int gap = UiTheme.GAP;
        int modeWidth = (contentWidth - gap) / 2;
        nearbyButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.nearby"),
                button -> setNearby(true)).bounds(left, UiTheme.CONTENT_TOP, modeWidth, 20).build());
        everywhereButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.everywhere"),
                button -> setNearby(false)).bounds(left + modeWidth + gap, UiTheme.CONTENT_TOP, modeWidth, 20).build());

        searchBox = new EditBox(font, left, UiTheme.CONTENT_TOP + 24, contentWidth, 20,
                Component.translatable("storagefinder.statistics.search"));
        searchBox.setHint(Component.translatable("storagefinder.statistics.search_hint"));
        searchBox.setValue(previousQuery);
        searchBox.setResponder(this::filter);
        addRenderableWidget(searchBox);

        int bottomY = height - 30;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        previousButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.previous"),
                button -> changePage(-1)).bounds(left, bottomY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.back"), button -> back())
                .bounds(left + buttonWidth + gap, bottomY, buttonWidth, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.next"),
                button -> changePage(1)).bounds(left + (buttonWidth + gap) * 2, bottomY, buttonWidth, 20).build());

        reloadEntries();
        setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        Component description = nearby
                ? Component.translatable("storagefinder.statistics.description",
                        StorageFinderConfig.current().searchRadiusChunks)
                : Component.translatable("storagefinder.statistics.description_everywhere");
        UiTheme.drawFrame(graphics, font, width, height, title, description);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int hovered = rowAt(mouseX, mouseY);
        int start = page * rows;
        int end = Math.min(filteredEntries.size(), start + rows);
        for (int index = start; index < end; index++) {
            Entry entry = filteredEntries.get(index);
            int rowY = LIST_TOP + (index - start) * ROW_HEIGHT;
            Integer searchColor = StorageFinderClient.searchColor(entry.stack());
            int background = index == hovered ? UiTheme.ROW_HOVER
                    : searchColor != null ? UiTheme.ROW_ACTIVE
                    : (index & 1) == 0 ? UiTheme.ROW : UiTheme.ROW_ALT;
            graphics.fill(left, rowY, left + contentWidth, rowY + ROW_HEIGHT - 1, background);
            if (searchColor != null) {
                graphics.fill(left, rowY, left + 3, rowY + ROW_HEIGHT - 1, searchColor);
            }
            graphics.item(entry.stack(), left + 5, rowY + 2);
            String amount = "× " + entry.count();
            int amountWidth = font.width(amount);
            graphics.text(font, UiTheme.trim(font, entry.label().getString(), contentWidth - 40 - amountWidth),
                    left + 27, rowY + 7, UiTheme.BODY, true);
            graphics.text(font, amount, left + contentWidth - amountWidth - 7, rowY + 7, UiTheme.SUCCESS, true);
        }
        int footerY = LIST_TOP + rows * ROW_HEIGHT + 4;
        if (filteredEntries.isEmpty()) {
            Component empty = allEntries.isEmpty()
                    ? Component.translatable("storagefinder.statistics.empty")
                    : Component.translatable("storagefinder.statistics.no_matches");
            graphics.textWithWordWrap(font, empty, left, LIST_TOP + 6, contentWidth, UiTheme.MUTED);
        } else {
            Component footer = Component.translatable("storagefinder.statistics.page", page + 1, pageCount());
            footer = footer.copy().append(Component.translatable("storagefinder.statistics.click_hint"));
            graphics.centeredText(font, UiTheme.trim(font, footer.getString(), contentWidth),
                    width / 2, footerY, UiTheme.MUTED);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = rowAt(event.x(), event.y());
        if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Entry entry = filteredEntries.get(index);
            StorageFinderClient.selectFromSearch(entry.stack(), entry.label());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && !filteredEntries.isEmpty()) {
            changePage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            back();
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
        back();
    }

    private int rowAt(double x, double y) {
        if (x < left || x >= left + contentWidth || y < LIST_TOP) {
            return -1;
        }
        int local = (int) ((y - LIST_TOP) / ROW_HEIGHT);
        int index = page * rows + local;
        return local < rows && index < filteredEntries.size() ? index : -1;
    }

    private int pageCount() {
        return Math.max(1, (filteredEntries.size() + rows - 1) / rows);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        updateButtons();
    }

    private void setNearby(boolean value) {
        nearby = value;
        reloadEntries();
    }

    private void updateButtons() {
        nearbyButton.active = !nearby;
        everywhereButton.active = nearby;
        previousButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
    }

    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filteredEntries = allEntries.stream().filter(entry -> needle.isEmpty()
                || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                || entry.key().toLowerCase(Locale.ROOT).contains(needle)).toList();
        page = 0;
        updateButtons();
    }

    private void reloadEntries() {
        allEntries = buildEntries(nearby
                ? StorageFinderClient.nearbyItemStatistics()
                : StorageFinderClient.allKnownItemStatistics());
        filter(searchBox == null ? "" : searchBox.getValue());
    }

    private List<Entry> buildEntries(Map<String, Integer> counts) {
        Map<Identifier, Holder<Enchantment>> enchantments = enchantmentHolders();
        List<Entry> result = new ArrayList<>();
        counts.forEach((key, count) -> {
            if (count <= 0) {
                return;
            }
            String baseId = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
            Identifier id = Identifier.tryParse(baseId);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) {
                return;
            }
            ItemStack stack = new ItemStack(item);
            Component label = stack.getHoverName();
            int marker = key.indexOf(ENCHANTMENT_MARKER);
            if (marker >= 0) {
                String enchantmentKey = key.substring(marker + ENCHANTMENT_MARKER.length());
                Identifier enchantmentId = Identifier.tryParse(enchantmentKey);
                Holder<Enchantment> holder = enchantmentId == null ? null : enchantments.get(enchantmentId);
                if (holder != null) {
                    ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    stored.set(holder, 1);
                    stack.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                    label = Component.translatable("storagefinder.statistics.enchanted", holder.value().description());
                } else {
                    label = Component.translatable("storagefinder.statistics.enchanted", enchantmentKey);
                }
            }
            result.add(new Entry(key, label, stack, count));
        });
        result.sort(Comparator.comparingInt(Entry::count).reversed()
                .thenComparing(entry -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private Map<Identifier, Holder<Enchantment>> enchantmentHolders() {
        Map<Identifier, Holder<Enchantment>> holders = new HashMap<>();
        if (minecraft != null && minecraft.level != null) {
            minecraft.level.registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(registry ->
                    registry.entrySet().forEach(entry -> holders.put(entry.getKey().identifier(),
                            registry.wrapAsHolder(entry.getValue()))));
        }
        return holders;
    }

    private void back() {
        minecraft.setScreenAndShow(new StorageSearchScreen());
    }

    private record Entry(String key, Component label, ItemStack stack, int count) {
    }
}

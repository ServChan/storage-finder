package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageFinderConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StorageStatisticsScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private EditBox searchBox;
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
        contentWidth = Math.min(440, width - 40);
        left = (width - contentWidth) / 2;
        rows = Math.max(3, Math.min(10, (height - 135) / ROW_HEIGHT));
        int gap = 8;
        int modeWidth = (contentWidth - gap) / 2;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.nearby"), button -> {
            nearby = true;
            reloadEntries();
        }).bounds(left, 43, modeWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.everywhere"), button -> {
            nearby = false;
            reloadEntries();
        }).bounds(left + modeWidth + gap, 43, modeWidth, 20).build());

        searchBox = new EditBox(font, left, 66, contentWidth, 20,
                Component.translatable("storagefinder.statistics.search"));
        searchBox.setHint(Component.translatable("storagefinder.statistics.search_hint"));
        searchBox.setResponder(this::filter);
        addRenderableWidget(searchBox);

        int bottomY = height - 30;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.previous"), button -> {
            page = Math.max(0, page - 1);
        }).bounds(left, bottomY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.back"), button -> back())
                .bounds(left + buttonWidth + gap, bottomY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.statistics.next"), button -> {
            int pages = Math.max(1, (filteredEntries.size() + rows - 1) / rows);
            page = Math.min(pages - 1, page + 1);
        }).bounds(left + (buttonWidth + gap) * 2, bottomY, buttonWidth, 20).build());

        reloadEntries();
        setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF010141C);
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        Component description = nearby
                ? Component.translatable("storagefinder.statistics.description",
                        StorageFinderConfig.current().searchRadiusChunks)
                : Component.translatable("storagefinder.statistics.description_everywhere");
        graphics.centeredText(font, description, width / 2, 27, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int start = page * rows;
        int end = Math.min(filteredEntries.size(), start + rows);
        int listY = 89;
        for (int index = start; index < end; index++) {
            Entry entry = filteredEntries.get(index);
            int rowY = listY + (index - start) * ROW_HEIGHT;
            graphics.fill(left, rowY, left + contentWidth, rowY + ROW_HEIGHT - 1,
                    (index & 1) == 0 ? 0xEE1B2430 : 0xEE202B38);
            graphics.item(entry.stack(), left + 4, rowY + 2);
            graphics.text(font, entry.label(), left + 27, rowY + 7, 0xFFF2F5F8, true);
            Component amount = Component.literal("× " + entry.count());
            graphics.text(font, amount, left + contentWidth - font.width(amount) - 7,
                    rowY + 7, 0xFF69F0AE, true);
        }
        if (filteredEntries.isEmpty()) {
            graphics.centeredText(font, Component.translatable("storagefinder.statistics.empty"),
                    width / 2, listY + 8, 0xFFFFA0A0);
        } else {
            int pages = Math.max(1, (filteredEntries.size() + rows - 1) / rows);
            graphics.centeredText(font, Component.translatable("storagefinder.statistics.page", page + 1, pages),
                    width / 2, height - 43, 0xFF9AA4B2);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            page = Math.min(Math.max(0, (filteredEntries.size() - 1) / rows), page + 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            page = Math.max(0, page - 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        back();
    }

    private void filter(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        filteredEntries = allEntries.stream().filter(entry -> needle.isEmpty()
                || entry.label().getString().toLowerCase(Locale.ROOT).contains(needle)
                || entry.key().toLowerCase(Locale.ROOT).contains(needle)).toList();
        page = 0;
    }

    private void reloadEntries() {
        allEntries = buildEntries(nearby
                ? StorageFinderClient.nearbyItemStatistics()
                : StorageFinderClient.allKnownItemStatistics());
        filter(searchBox == null ? "" : searchBox.getValue());
    }

    private static List<Entry> buildEntries(Map<String, Integer> counts) {
        List<Entry> result = new ArrayList<>();
        counts.forEach((key, count) -> {
            if (count <= 0) return;
            String baseId = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
            Identifier id = Identifier.tryParse(baseId);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == Items.AIR) return;
            ItemStack stack = new ItemStack(item);
            Component label = key.contains("|stored_enchantment=")
                    ? Component.translatable("storagefinder.statistics.enchanted",
                            key.substring(key.indexOf('=') + 1))
                    : stack.getHoverName();
            result.add(new Entry(key, label, stack, count));
        });
        result.sort(Comparator.comparingInt(Entry::count).reversed()
                .thenComparing(entry -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private void back() {
        minecraft.setScreenAndShow(new StorageSearchScreen());
    }

    private record Entry(String key, Component label, ItemStack stack, int count) {
    }
}

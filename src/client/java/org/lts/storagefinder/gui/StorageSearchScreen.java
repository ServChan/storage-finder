package org.lts.storagefinder.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;
import org.lts.storagefinder.SearchSelection;
import org.lts.storagefinder.StorageFinderClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class StorageSearchScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;

    private EditBox searchBox;
    private List<Suggestion> catalog = List.of();
    private List<Suggestion> visibleSuggestions = List.of();
    private int selectedSuggestion;
    private int fieldX;
    private int fieldY;
    private int fieldWidth;
    private int visibleRows;

    public StorageSearchScreen() {
        super(Component.translatable("storagefinder.search.title"));
    }

    @Override
    protected void init() {
        fieldWidth = Math.min(440, this.width - 40);
        fieldX = (this.width - fieldWidth) / 2;
        fieldY = 48;
        visibleRows = Math.max(3, Math.min(MAX_VISIBLE_SUGGESTIONS,
                (this.height - fieldY - 78) / ROW_HEIGHT));

        searchBox = new EditBox(this.font, fieldX, fieldY, fieldWidth, 20,
                Component.translatable("storagefinder.search.input"));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("storagefinder.search.hint"));
        searchBox.setResponder(this::updateSuggestions);
        addRenderableWidget(searchBox);

        int buttonY = this.height - 30;
        int gap = 8;
        int buttonWidth = (fieldWidth - gap) / 2;
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.clear"), button -> {
            StorageFinderClient.clearSearch();
            closeScreen();
        }).bounds(fieldX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("storagefinder.search.cancel"), button -> closeScreen())
                .bounds(fieldX + buttonWidth + gap, buttonY, buttonWidth, 20).build());

        catalog = buildCatalog();
        updateSuggestions("");
        setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF010141C);
        graphics.centeredText(this.font, this.title, this.width / 2, 17, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("storagefinder.search.description"),
                this.width / 2, 31, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        int listY = fieldY + 23;
        for (int index = 0; index < visibleSuggestions.size(); index++) {
            Suggestion suggestion = visibleSuggestions.get(index);
            int rowY = listY + index * ROW_HEIGHT;
            Integer searchColor = StorageFinderClient.searchColor(suggestion.stack());
            int background = index == selectedSuggestion ? 0xEE355A7A
                    : searchColor != null ? 0xEE24382F : 0xEE1B2430;
            graphics.fill(fieldX, rowY, fieldX + fieldWidth, rowY + ROW_HEIGHT - 1, background);
            graphics.outline(fieldX, rowY, fieldWidth, ROW_HEIGHT - 1,
                    index == selectedSuggestion ? 0xFF70C7FF
                            : searchColor != null ? searchColor : 0xFF394554);
            graphics.item(suggestion.icon(), fieldX + 3, rowY + 2);
            Component label = searchColor == null ? suggestion.label()
                    : Component.translatable("storagefinder.search.active", suggestion.label());
            graphics.text(this.font, label, fieldX + 24, rowY + 7, 0xFFFFFFFF);
        }
        if (visibleSuggestions.isEmpty() && !searchBox.getValue().isBlank()) {
            graphics.centeredText(this.font, Component.translatable("storagefinder.search.no_results"),
                    this.width / 2, listY + 8, 0xFFFFA0A0);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int listY = fieldY + 23;
            if (event.x() >= fieldX && event.x() < fieldX + fieldWidth && event.y() >= listY) {
                int index = (int) ((event.y() - listY) / ROW_HEIGHT);
                if (index >= 0 && index < visibleSuggestions.size()) {
                    choose(visibleSuggestions.get(index));
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
        if (event.key() == GLFW.GLFW_KEY_DOWN && !visibleSuggestions.isEmpty()) {
            selectedSuggestion = (selectedSuggestion + 1) % visibleSuggestions.size();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP && !visibleSuggestions.isEmpty()) {
            selectedSuggestion = (selectedSuggestion - 1 + visibleSuggestions.size()) % visibleSuggestions.size();
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && !visibleSuggestions.isEmpty()) {
            choose(visibleSuggestions.get(selectedSuggestion));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private List<Suggestion> buildCatalog() {
        List<Suggestion> suggestions = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == Items.ENCHANTED_BOOK) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            Component label = stack.getHoverName();
            suggestions.add(new Suggestion(label, stack));
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
                        suggestions.add(new Suggestion(label, stack));
                    }));
        }

        Map<String, Long> labelCounts = suggestions.stream().collect(Collectors.groupingBy(
                suggestion -> suggestion.label().getString(), Collectors.counting()));
        List<Suggestion> disambiguated = suggestions.stream().map(suggestion -> {
            if (labelCounts.getOrDefault(suggestion.label().getString(), 0L) <= 1L) {
                return suggestion;
            }
            String identity = String.join(", ", SearchSelection.itemIds(suggestion.stack()));
            return new Suggestion(Component.translatable("storagefinder.search.duplicate",
                    suggestion.label(), identity), suggestion.stack());
        }).collect(Collectors.toCollection(ArrayList::new));

        disambiguated.sort(Comparator.comparing(suggestion -> suggestion.label().getString(),
                String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(disambiguated);
    }

    private void updateSuggestions(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        visibleSuggestions = catalog.stream()
                .filter(suggestion -> needle.isEmpty()
                        || suggestion.label().getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingInt((Suggestion suggestion) ->
                                StorageFinderClient.searchColor(suggestion.stack()) == null ? 1 : 0)
                        .thenComparingInt(suggestion -> matchRank(suggestion, needle))
                        .thenComparing(suggestion -> suggestion.label().getString(), String.CASE_INSENSITIVE_ORDER))
                .limit(visibleRows)
                .toList();
        selectedSuggestion = 0;
    }

    private static int matchRank(Suggestion suggestion, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        String label = suggestion.label().getString().toLowerCase(Locale.ROOT);
        if (label.equals(needle)) {
            return 0;
        }
        if (label.startsWith(needle) || label.endsWith(needle)) {
            return 1;
        }
        return label.contains(" " + needle) ? 2 : 3;
    }

    private void choose(Suggestion suggestion) {
        StorageFinderClient.selectFromSearch(suggestion.stack(), suggestion.label());
        closeScreen();
    }

    private void closeScreen() {
        this.minecraft.setScreen(null);
    }

    private record Suggestion(Component label, ItemStack stack) {
        ItemStack icon() {
            return stack;
        }
    }
}

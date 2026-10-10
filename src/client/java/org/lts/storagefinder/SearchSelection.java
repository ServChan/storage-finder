package org.lts.storagefinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SearchSelection {
    private final LinkedHashMap<Integer, Query> queries = new LinkedHashMap<>();
    private Integer pinnedColor;
    private long version;

    public Change toggle(ItemStack stack, Component displayName) {
        Set<String> itemIds = itemIds(stack);
        String itemId = itemIds.iterator().next();
        List<Integer> existing = queries.values().stream()
                .filter(query -> query.kind() == Kind.ITEM && itemIds.stream().anyMatch(query.keys()::contains))
                .map(Query::color)
                .toList();
        if (!existing.isEmpty()) {
            existing.forEach(this::forget);
            return new Change(false, itemId, existing.getFirst());
        }
        int color = nextColor();
        add(new Query(color, Kind.ITEM, stack.copy(), displayName.copy(), Set.copyOf(itemIds), null, null, null, null));
        return new Change(true, itemId, color);
    }

    public Change toggleGroup(String needle, ItemStack icon, Component displayName, Set<String> keys,
                              boolean matchCustomNames) {
        Integer existing = groupColor(needle);
        if (existing != null) {
            forget(existing);
            return new Change(false, needle, existing);
        }
        int color = nextColor();
        add(new Query(color, Kind.GROUP, icon.copy(), displayName.copy(), Set.copyOf(keys),
                matchCustomNames ? ItemKeys.normalizeName(needle) : null, needle, null, null));
        return new Change(true, needle, color);
    }

    public Change togglePosition(String scope, BlockPos pos, ItemStack icon, Component displayName) {
        for (Query query : queries.values()) {
            if (query.kind() == Kind.POSITION && scope.equals(query.scope()) && pos.equals(query.pos())) {
                forget(query.color());
                return new Change(false, scope, query.color());
            }
        }
        int color = nextColor();
        add(new Query(color, Kind.POSITION, icon.copy(), displayName.copy(), Set.of(), null, null, scope,
                pos.immutable()));
        return new Change(true, scope, color);
    }

    public Integer groupColor(String needle) {
        for (Query query : queries.values()) {
            if (query.kind() == Kind.GROUP && needle.equals(query.groupId())) {
                return query.color();
            }
        }
        return null;
    }

    public int removeColors(Collection<Integer> colors) {
        int removed = 0;
        for (Integer color : colors) {
            if (!color.equals(pinnedColor) && queries.containsKey(color)) {
                forget(color);
                removed++;
            }
        }
        return removed;
    }

    public boolean togglePinned(ItemStack stack) {
        Integer color = colorFor(stack);
        if (color == null) {
            return false;
        }
        pinnedColor = color.equals(pinnedColor) ? null : color;
        version++;
        return color.equals(pinnedColor);
    }

    public boolean isPinned(ItemStack stack) {
        Integer color = colorFor(stack);
        return color != null && color.equals(pinnedColor);
    }

    public void clear() {
        queries.clear();
        pinnedColor = null;
        version++;
    }

    public boolean isEmpty() {
        return queries.isEmpty();
    }

    public Integer colorFor(ItemStack stack) {
        Set<String> keys = itemIds(stack);
        for (Query query : queries.values()) {
            if (query.kind() == Kind.ITEM && keys.stream().anyMatch(query.keys()::contains)) {
                return query.color();
            }
        }
        return null;
    }

    public Collection<Query> active() {
        return Collections.unmodifiableCollection(queries.values());
    }

    public List<Query> queries() {
        return List.copyOf(queries.values());
    }

    public long version() {
        return version;
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static Set<String> itemIds(ItemStack stack) {
        String itemId = itemId(stack);
        if (stack.getItem() != Items.ENCHANTED_BOOK) {
            return Set.of(itemId);
        }

        ItemEnchantments enchantments = stack.getOrDefault(
                DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        LinkedHashSet<String> keys = enchantments.keySet().stream()
                .map(holder -> holder.unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElseGet(holder::getRegisteredName))
                .sorted()
                .map(enchantmentId -> itemId + ItemKeys.ENCHANTMENT_MARKER + enchantmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (keys.isEmpty()) {
            keys.add(itemId + ItemKeys.ENCHANTMENT_MARKER);
        }
        return Collections.unmodifiableSet(keys);
    }

    public static Component displayName(ItemStack stack) {
        if (stack.getItem() != Items.ENCHANTED_BOOK) {
            return stack.getHoverName();
        }
        ItemEnchantments enchantments = stack.getOrDefault(
                DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<String> enchantmentNames = enchantments.keySet().stream()
                .map(holder -> holder.value().description().getString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (enchantmentNames.isEmpty()) {
            return stack.getHoverName();
        }
        String names = String.join(", ", enchantmentNames);
        return Component.translatable(enchantmentNames.size() == 1
                ? "storagefinder.search.enchanted_book"
                : "storagefinder.search.enchanted_book_any", names);
    }

    private void add(Query query) {
        queries.put(query.color(), query);
        version++;
    }

    private void forget(int color) {
        queries.remove(color);
        if (pinnedColor != null && pinnedColor == color) {
            pinnedColor = null;
        }
        version++;
    }

    private int nextColor() {
        return SearchPalette.next(new LinkedHashSet<>(queries.keySet()));
    }

    public enum Kind {
        ITEM,
        GROUP,
        POSITION
    }

    public record Change(boolean added, String itemId, int color) {
    }

    public record Query(int color, Kind kind, ItemStack stack, Component displayName, Set<String> keys,
                        String nameNeedle, String groupId, String scope, BlockPos pos) {
        public boolean matchesKey(String key) {
            if (keys.contains(key)) {
                return true;
            }
            return nameNeedle != null && !nameNeedle.isEmpty() && ItemKeys.isNameKey(key)
                    && ItemKeys.nameOf(key).contains(nameNeedle);
        }

        public boolean matches(Map<String, Integer> contents) {
            if (kind == Kind.POSITION || contents.isEmpty()) {
                return false;
            }
            if (nameNeedle == null && keys.size() <= contents.size()) {
                for (String key : keys) {
                    if (contents.containsKey(key)) {
                        return true;
                    }
                }
                return false;
            }
            for (String key : contents.keySet()) {
                if (matchesKey(key)) {
                    return true;
                }
            }
            return false;
        }

        public int countIn(Map<String, Integer> contents) {
            if (kind == Kind.POSITION || contents.isEmpty()) {
                return 0;
            }
            Map<String, Integer> byBase = new HashMap<>();
            for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                if (matchesKey(entry.getKey())) {
                    byBase.merge(ItemKeys.baseId(entry.getKey()), entry.getValue(), Math::max);
                }
            }
            int total = 0;
            for (int count : byBase.values()) {
                total = ItemKeys.addCounts(total, count);
            }
            return total;
        }

        public Set<String> priceKeys() {
            return kind == Kind.ITEM ? keys : Set.of();
        }
    }
}

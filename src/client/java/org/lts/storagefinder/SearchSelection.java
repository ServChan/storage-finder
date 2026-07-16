package org.lts.storagefinder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SearchSelection {
    private static final int[] COLORS = {
            0xFFFF5252, 0xFF40C4FF, 0xFFFFD740, 0xFF69F0AE,
            0xFFE040FB, 0xFFFF6E40, 0xFF7C4DFF, 0xFF18FFFF
    };

    private final LinkedHashMap<String, Integer> selected = new LinkedHashMap<>();

    public Change toggle(ItemStack stack) {
        Set<String> itemIds = itemIds(stack);
        String itemId = itemIds.iterator().next();
        Integer oldColor = itemIds.stream().map(selected::get).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (oldColor != null) {
            itemIds.forEach(selected::remove);
            return new Change(false, itemId, oldColor);
        }

        int color = nextColor();
        itemIds.forEach(key -> selected.put(key, color));
        return new Change(true, itemId, color);
    }

    public int removeColors(Collection<Integer> colors) {
        int previousSize = selected.size();
        selected.entrySet().removeIf(entry -> colors.contains(entry.getValue()));
        return previousSize - selected.size();
    }

    public void clear() {
        selected.clear();
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public Integer colorFor(ItemStack stack) {
        return itemIds(stack).stream()
                .map(selected::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public Map<String, Integer> entries() {
        return Collections.unmodifiableMap(selected);
    }

    public static String itemId(ItemStack stack) {
        return itemIds(stack).iterator().next();
    }

    public static Set<String> itemIds(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
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
                .map(enchantmentId -> itemId + "|stored_enchantment=" + enchantmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (keys.isEmpty()) {
            keys.add(itemId + "|stored_enchantment=");
        }
        return Collections.unmodifiableSet(keys);
    }

    private int nextColor() {
        for (int color : COLORS) {
            if (!selected.containsValue(color)) {
                return color;
            }
        }
        int index = selected.size();
        float hue = (index * 0.61803398875F) % 1.0F;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0xFFFFFF;
    }

    public record Change(boolean added, String itemId, int color) {
    }
}

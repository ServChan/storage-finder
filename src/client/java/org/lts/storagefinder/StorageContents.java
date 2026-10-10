package org.lts.storagefinder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StorageContents {
    private static final int MAX_NESTING = 2;

    private StorageContents() {
    }

    public static Map<String, Integer> collect(AbstractContainerMenu menu, int slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int limit = Math.min(slots, menu.slots.size());
        for (int slot = 0; slot < limit; slot++) {
            add(menu.getSlot(slot).getItem(), counts);
        }
        return counts;
    }

    public static Map<String, Integer> collect(Inventory inventory, ItemStack carried) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(inventory.getItem(slot), counts);
        }
        add(carried, counts);
        return counts;
    }

    public static Map<String, Integer> of(ItemStack stack) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        add(stack, counts);
        return counts;
    }

    public static void add(ItemStack stack, Map<String, Integer> counts) {
        add(stack, counts, 0);
    }

    public static int fingerprint(AbstractContainerMenu menu, int slots) {
        int hash = 1;
        int limit = Math.min(slots, menu.slots.size());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            hash = 31 * hash + System.identityHashCode(stack);
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }

    public static boolean hasContents(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }

    private static void add(ItemStack stack, Map<String, Integer> counts, int depth) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int count = stack.getCount();
        for (String key : SearchSelection.itemIds(stack)) {
            counts.merge(key, count, ItemKeys::addCounts);
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            String nameKey = ItemKeys.nameKey(SearchSelection.itemId(stack), customName.getString());
            if (nameKey != null) {
                counts.merge(nameKey, count, ItemKeys::addCounts);
            }
        }
        if (depth >= MAX_NESTING) {
            return;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStackTemplate inner : container.nonEmptyItems()) {
                add(inner.create(), counts, depth + 1);
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStackTemplate inner : bundle.items()) {
                add(inner.create(), counts, depth + 1);
            }
        }
    }
}

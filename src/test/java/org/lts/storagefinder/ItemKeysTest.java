package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;

final class ItemKeysTest {
    @Test
    void nameKeysAreNormalizedAndRecognised() {
        String key = ItemKeys.nameKey("minecraft:diamond_sword", "  Меч   Судьбы ");
        assertEquals("minecraft:diamond_sword|name=меч судьбы", key);
        assertTrue(ItemKeys.isNameKey(key));
        assertEquals("меч судьбы", ItemKeys.nameOf(key));
        assertEquals("minecraft:diamond_sword", ItemKeys.baseId(key));
        assertNull(ItemKeys.nameKey("minecraft:stone", "   "));
        assertFalse(ItemKeys.isNameKey("minecraft:stone"));
    }

    @Test
    void longNamesAreTruncated() {
        assertEquals(ItemKeys.MAX_NAME_LENGTH, ItemKeys.normalizeName("x".repeat(500)).length());
    }

    @Test
    void legacyEnchantmentKeysExpandToSingleEnchantments() {
        assertEquals(Set.of("minecraft:enchanted_book|stored_enchantment=a",
                        "minecraft:enchanted_book|stored_enchantment=b"),
                ItemKeys.expandLegacy("minecraft:enchanted_book|stored_enchantments=a,b"));
        assertEquals(Set.of("minecraft:enchanted_book|stored_enchantment="),
                ItemKeys.expandLegacy("minecraft:enchanted_book|stored_enchantments="));
        assertEquals(Set.of("minecraft:stone"), ItemKeys.expandLegacy("minecraft:stone"));
    }

    @Test
    void countsSaturateInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, ItemKeys.addCounts(Integer.MAX_VALUE, 5));
        assertEquals(7, ItemKeys.addCounts(3, 4));
    }
}

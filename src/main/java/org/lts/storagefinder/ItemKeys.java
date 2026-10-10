package org.lts.storagefinder;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ItemKeys {
    public static final String ENCHANTMENT_MARKER = "|stored_enchantment=";
    public static final String NAME_MARKER = "|name=";
    public static final int MAX_KEY_LENGTH = 512;
    public static final int MAX_NAME_LENGTH = 64;
    private static final String LEGACY_ENCHANTMENTS_MARKER = "|stored_enchantments=";

    private ItemKeys() {
    }

    public static boolean isValid(String key) {
        return key != null && !key.isBlank() && key.length() <= MAX_KEY_LENGTH;
    }

    public static String baseId(String key) {
        int marker = key.indexOf('|');
        return marker < 0 ? key : key.substring(0, marker);
    }

    public static boolean isNameKey(String key) {
        return key.contains(NAME_MARKER);
    }

    public static String nameOf(String key) {
        int marker = key.indexOf(NAME_MARKER);
        return marker < 0 ? "" : key.substring(marker + NAME_MARKER.length());
    }

    public static String nameKey(String itemId, String name) {
        String normalized = normalizeName(name);
        return normalized.isEmpty() ? null : itemId + NAME_MARKER + normalized;
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.length() > MAX_NAME_LENGTH ? normalized.substring(0, MAX_NAME_LENGTH) : normalized;
    }

    public static Set<String> expandLegacy(String key) {
        Set<String> expanded = new LinkedHashSet<>();
        int markerIndex = key.indexOf(LEGACY_ENCHANTMENTS_MARKER);
        if (markerIndex < 0) {
            expanded.add(key);
            return expanded;
        }
        String itemId = key.substring(0, markerIndex);
        String enchantments = key.substring(markerIndex + LEGACY_ENCHANTMENTS_MARKER.length());
        if (enchantments.isEmpty()) {
            expanded.add(itemId + ENCHANTMENT_MARKER);
            return expanded;
        }
        for (String enchantment : enchantments.split(",")) {
            if (!enchantment.isBlank()) {
                expanded.add(itemId + ENCHANTMENT_MARKER + enchantment);
            }
        }
        return expanded;
    }

    public static int addCounts(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
    }
}

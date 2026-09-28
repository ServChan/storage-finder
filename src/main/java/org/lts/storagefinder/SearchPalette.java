package org.lts.storagefinder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SearchPalette {
    private static final int[] PRESET = {
            0xFFFF5252, 0xFF40C4FF, 0xFFFFD740, 0xFF69F0AE,
            0xFFE040FB, 0xFFFF6E40, 0xFF7C4DFF, 0xFF18FFFF
    };
    private static final float GOLDEN_RATIO_CONJUGATE = 0.61803398875F;
    private static final int MAX_GENERATED = 4_096;

    private SearchPalette() {
    }

    public static int next(Collection<Integer> used) {
        Set<Integer> taken = new HashSet<>(used);
        for (int color : PRESET) {
            if (!taken.contains(color)) {
                return color;
            }
        }
        for (int index = 0; index < MAX_GENERATED; index++) {
            float hue = ((PRESET.length + index) * GOLDEN_RATIO_CONJUGATE) % 1.0F;
            float saturation = 0.65F + (index % 3) * 0.1F;
            int color = 0xFF000000 | hsbToRgb(hue, saturation, 1.0F);
            if (!taken.contains(color)) {
                return color;
            }
        }
        int fallback = 0xFF000001;
        while (taken.contains(fallback)) {
            fallback++;
        }
        return fallback;
    }

    static int hsbToRgb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - saturation * fraction);
        float t = brightness * (1.0F - saturation * (1.0F - fraction));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = brightness; g = t; b = p; }
            case 1 -> { r = q; g = brightness; b = p; }
            case 2 -> { r = p; g = brightness; b = t; }
            case 3 -> { r = p; g = q; b = brightness; }
            case 4 -> { r = t; g = p; b = brightness; }
            default -> { r = brightness; g = p; b = q; }
        }
        return channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}

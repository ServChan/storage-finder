package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SearchPaletteTest {
    @Test
    void firstColorIsTheFirstPreset() {
        assertEquals(0xFFFF5252, SearchPalette.next(List.of()));
    }

    @Test
    void freedPresetIsReused() {
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            used.add(SearchPalette.next(used));
        }
        int freed = used.remove(3);
        assertEquals(freed, SearchPalette.next(used));
    }

    @Test
    void generatedColorsNeverCollideAfterRemovals() {
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            used.add(SearchPalette.next(used));
        }
        used.remove(10);
        used.remove(25);
        used.remove(used.size() - 1);
        for (int i = 0; i < 20; i++) {
            int color = SearchPalette.next(used);
            assertFalse(used.contains(color));
            used.add(color);
        }
        Set<Integer> unique = new HashSet<>(used);
        assertEquals(used.size(), unique.size());
    }

    @Test
    void colorsAreOpaque() {
        List<Integer> used = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            int color = SearchPalette.next(used);
            assertEquals(0xFF, color >>> 24);
            used.add(color);
        }
    }

    @Test
    void hsbConversionMatchesPrimaryHues() {
        assertEquals(0xFF0000, SearchPalette.hsbToRgb(0.0F, 1.0F, 1.0F));
        assertEquals(0x00FF00, SearchPalette.hsbToRgb(1.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0x0000FF, SearchPalette.hsbToRgb(2.0F / 3.0F, 1.0F, 1.0F));
        assertTrue(SearchPalette.hsbToRgb(0.5F, 0.0F, 1.0F) == 0xFFFFFF);
    }
}

package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

final class IndexCodecTest {
    @Test
    void currentFormatRoundTrips() throws IOException {
        List<IndexRecord> records = List.of(
                new IndexRecord("srv|minecraft:overworld", 1, 64, -3, "chest",
                        Map.of("minecraft:stone", 64, "minecraft:diamond_sword|name=excalibur", 1), 42L),
                new IndexRecord("srv|ender:abc", 0, 0, 0, null, Map.of(), 7L));
        StringWriter output = new StringWriter();
        IndexCodec.write(records, output, false);

        IndexCodec.Decoded decoded = IndexCodec.read(new StringReader(output.toString()));
        assertFalse(decoded.migrated());
        assertEquals(records, decoded.records());
        assertFalse(output.toString().contains("\n"));
    }

    @Test
    void legacyFormatIsMigratedWithUnknownCountsKept() throws IOException {
        String legacy = """
                {"s|1,2,3": {"scope": "s", "x": 1, "y": 2, "z": 3,
                  "items": ["minecraft:stone", "minecraft:enchanted_book|stored_enchantments=minecraft:a,minecraft:b"],
                  "counts": {"minecraft:stone": 5}, "updatedAt": 99},
                 "broken": {"x": 1}}
                """;
        IndexCodec.Decoded decoded = IndexCodec.read(new StringReader(legacy));
        assertTrue(decoded.migrated());
        assertEquals(1, decoded.records().size());
        IndexRecord record = decoded.records().getFirst();
        assertNull(record.block());
        assertEquals(99L, record.updatedAt());
        assertEquals(Map.of(
                "minecraft:stone", 5,
                "minecraft:enchanted_book|stored_enchantment=minecraft:a", 0,
                "minecraft:enchanted_book|stored_enchantment=minecraft:b", 0), record.counts());
    }

    @Test
    void invalidEntriesAreDroppedAndReported() throws IOException {
        String json = """
                {"version": 2, "scopes": {"s": [{"x": 0, "y": 0, "z": 0, "t": 1, "c": {"": 4, "a": 2}}, 5]}}
                """;
        IndexCodec.Decoded decoded = IndexCodec.read(new StringReader(json));
        assertTrue(decoded.migrated());
        assertEquals(Map.of("a", 2), decoded.records().getFirst().counts());
    }

    @Test
    void newerVersionsAndNonObjectsAreRejected() {
        assertThrows(IOException.class, () -> IndexCodec.read(new StringReader("{\"version\": 99, \"scopes\": {}}")));
        assertThrows(IOException.class, () -> IndexCodec.read(new StringReader("null")));
        assertThrows(IOException.class, () -> IndexCodec.read(new StringReader("[1, 2")));
    }
}

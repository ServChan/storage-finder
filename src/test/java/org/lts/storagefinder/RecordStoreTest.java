package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

final class RecordStoreTest {
    @Test
    void nearReturnsOnlyRecordsInTheChunkSquare() {
        RecordStore store = new RecordStore();
        store.put(record("a", 0, 0));
        store.put(record("a", 40, 0));
        store.put(record("a", -17, 31));
        store.put(record("a", 500, 500));
        store.put(record("b", 0, 0));

        List<IndexRecord> near = store.near("a", 0, 0, 2);
        assertEquals(3, near.size());
        assertTrue(near.stream().allMatch(record -> record.scope().equals("a")));
        assertEquals(1, store.near("a", 31, 31, 0).size());
        assertEquals(List.of(), store.near("missing", 0, 0, 8));
    }

    @Test
    void nearScansStoredChunksWhenTheRadiusIsLarge() {
        RecordStore store = new RecordStore();
        store.put(record("a", 0, 0));
        store.put(record("a", 16 * 300, 0));
        assertEquals(2, store.near("a", 0, 0, 400).size());
        assertEquals(1, store.near("a", 0, 0, 299).size());
    }

    @Test
    void replacingARecordKeepsScopeCountsAndChunkIndexConsistent() {
        RecordStore store = new RecordStore();
        store.put(record("a", 5, 5));
        store.put(record("a", 5, 5));
        assertEquals(1, store.scopeSize("a"));
        assertEquals(1, store.near("a", 0, 0, 0).size());

        assertTrue(store.remove(RecordStore.key("a", 5, 64, 5)) != null);
        assertEquals(0, store.scopeSize("a"));
        assertFalse(store.hasScope("a"));
        assertEquals(List.of(), store.near("a", 0, 0, 0));
        assertNull(store.remove(RecordStore.key("a", 5, 64, 5)));
    }

    @Test
    void removeScopeDropsEveryRecordOfThatScope() {
        RecordStore store = new RecordStore();
        store.put(record("a", 0, 0));
        store.put(record("a", 100, 100));
        store.put(record("b", 0, 0));
        long before = store.revision();

        assertEquals(2, store.removeScope("a"));
        assertEquals(1, store.size());
        assertEquals(List.of(), store.inScope("a"));
        assertTrue(store.revision() > before);
    }

    private static IndexRecord record(String scope, int x, int z) {
        return new IndexRecord(scope, x, 64, z, "chest", Map.of("minecraft:stone", 1), 1L);
    }
}

package org.lts.storagefinder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record IndexRecord(String scope, int x, int y, int z, String block,
                          Map<String, Integer> counts, long updatedAt) {
    public IndexRecord {
        Objects.requireNonNull(scope, "scope");
        counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public Set<String> items() {
        return counts.keySet();
    }

    public String key() {
        return RecordStore.key(scope, x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public long totalCount() {
        long total = 0L;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!ItemKeys.isNameKey(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public IndexRecord inScope(String newScope) {
        return new IndexRecord(newScope, x, y, z, block, counts, updatedAt);
    }
}

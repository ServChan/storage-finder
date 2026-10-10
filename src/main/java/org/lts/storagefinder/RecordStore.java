package org.lts.storagefinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class RecordStore {
    private final Map<String, IndexRecord> byKey = new LinkedHashMap<>();
    private final Map<String, Map<Long, Map<String, IndexRecord>>> byScopeChunk = new HashMap<>();
    private final Map<String, Integer> scopeCounts = new HashMap<>();
    private long revision;

    public static String key(String scope, int x, int y, int z) {
        return scope + "|" + x + "," + y + "," + z;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public IndexRecord get(String key) {
        return byKey.get(key);
    }

    public IndexRecord get(String scope, int x, int y, int z) {
        return byKey.get(key(scope, x, y, z));
    }

    public IndexRecord put(IndexRecord record) {
        String key = record.key();
        IndexRecord previous = byKey.put(key, record);
        if (previous != null) {
            unlinkChunk(previous, key);
        } else {
            scopeCounts.merge(record.scope(), 1, Integer::sum);
        }
        byScopeChunk.computeIfAbsent(record.scope(), ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey(record.chunkX(), record.chunkZ()), ignored -> new LinkedHashMap<>())
                .put(key, record);
        revision++;
        return previous;
    }

    public IndexRecord remove(String key) {
        IndexRecord removed = byKey.remove(key);
        if (removed == null) {
            return null;
        }
        unlinkChunk(removed, key);
        scopeCounts.computeIfPresent(removed.scope(), (ignored, count) -> count <= 1 ? null : count - 1);
        revision++;
        return removed;
    }

    public int removeAll(Collection<String> keys) {
        int removed = 0;
        for (String key : keys) {
            if (remove(key) != null) {
                removed++;
            }
        }
        return removed;
    }

    public int removeScope(String scope) {
        Map<Long, Map<String, IndexRecord>> chunks = byScopeChunk.remove(scope);
        if (chunks == null) {
            return 0;
        }
        int removed = 0;
        for (Map<String, IndexRecord> chunk : chunks.values()) {
            for (String key : chunk.keySet()) {
                if (byKey.remove(key) != null) {
                    removed++;
                }
            }
        }
        scopeCounts.remove(scope);
        revision++;
        return removed;
    }

    public void replaceAll(Collection<IndexRecord> records) {
        byKey.clear();
        byScopeChunk.clear();
        scopeCounts.clear();
        for (IndexRecord record : records) {
            put(record);
        }
        revision++;
    }

    public int size() {
        return byKey.size();
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public int scopeSize(String scope) {
        return scopeCounts.getOrDefault(scope, 0);
    }

    public boolean hasScope(String scope) {
        return scopeCounts.containsKey(scope);
    }

    public Map<String, Integer> scopeCounts() {
        return Collections.unmodifiableMap(scopeCounts);
    }

    public Collection<IndexRecord> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public List<IndexRecord> snapshot() {
        return List.copyOf(byKey.values());
    }

    public void forEachInScope(String scope, Consumer<IndexRecord> consumer) {
        Map<Long, Map<String, IndexRecord>> chunks = byScopeChunk.get(scope);
        if (chunks == null) {
            return;
        }
        for (Map<String, IndexRecord> chunk : chunks.values()) {
            chunk.values().forEach(consumer);
        }
    }

    public List<IndexRecord> inScope(String scope) {
        List<IndexRecord> result = new ArrayList<>(scopeSize(scope));
        forEachInScope(scope, result::add);
        return result;
    }

    public List<IndexRecord> near(String scope, int chunkX, int chunkZ, int chunkRadius) {
        Map<Long, Map<String, IndexRecord>> chunks = byScopeChunk.get(scope);
        if (chunks == null) {
            return List.of();
        }
        List<IndexRecord> result = new ArrayList<>();
        long side = 2L * chunkRadius + 1L;
        if (side * side > chunks.size()) {
            for (Map<String, IndexRecord> chunk : chunks.values()) {
                for (IndexRecord record : chunk.values()) {
                    if (Math.abs(record.chunkX() - chunkX) <= chunkRadius
                            && Math.abs(record.chunkZ() - chunkZ) <= chunkRadius) {
                        result.add(record);
                    }
                }
            }
            return result;
        }
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Map<String, IndexRecord> chunk = chunks.get(chunkKey(chunkX + dx, chunkZ + dz));
                if (chunk != null) {
                    result.addAll(chunk.values());
                }
            }
        }
        return result;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(byKey.keySet());
    }

    public long revision() {
        return revision;
    }

    private void unlinkChunk(IndexRecord record, String key) {
        Map<Long, Map<String, IndexRecord>> chunks = byScopeChunk.get(record.scope());
        if (chunks == null) {
            return;
        }
        long chunkKey = chunkKey(record.chunkX(), record.chunkZ());
        Map<String, IndexRecord> chunk = chunks.get(chunkKey);
        if (chunk == null) {
            return;
        }
        chunk.remove(key);
        if (chunk.isEmpty()) {
            chunks.remove(chunkKey);
        }
        if (chunks.isEmpty()) {
            byScopeChunk.remove(record.scope());
        }
    }
}

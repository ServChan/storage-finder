package org.lts.storagefinder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IndexCodec {
    public static final int VERSION = 2;

    private IndexCodec() {
    }

    public static void write(Collection<IndexRecord> records, Writer output, boolean pretty) throws IOException {
        Map<String, List<IndexRecord>> byScope = new LinkedHashMap<>();
        for (IndexRecord record : records) {
            byScope.computeIfAbsent(record.scope(), ignored -> new ArrayList<>()).add(record);
        }
        JsonWriter writer = new JsonWriter(output);
        if (pretty) {
            writer.setIndent("  ");
        }
        writer.beginObject();
        writer.name("version").value(VERSION);
        writer.name("scopes").beginObject();
        for (Map.Entry<String, List<IndexRecord>> scope : byScope.entrySet()) {
            writer.name(scope.getKey()).beginArray();
            for (IndexRecord record : scope.getValue()) {
                writer.beginObject();
                writer.name("x").value(record.x());
                writer.name("y").value(record.y());
                writer.name("z").value(record.z());
                if (record.block() != null) {
                    writer.name("b").value(record.block());
                }
                writer.name("t").value(record.updatedAt());
                writer.name("c").beginObject();
                for (Map.Entry<String, Integer> count : record.counts().entrySet()) {
                    writer.name(count.getKey()).value(count.getValue());
                }
                writer.endObject();
                writer.endObject();
            }
            writer.endArray();
        }
        writer.endObject();
        writer.endObject();
        writer.flush();
    }

    public static Decoded read(Reader input) throws IOException {
        JsonElement root;
        try {
            root = JsonParser.parseReader(input);
        } catch (RuntimeException exception) {
            throw new IOException("Storage index is not valid JSON", exception);
        }
        if (root == null || !root.isJsonObject()) {
            throw new IOException("Storage index root must be a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        if (object.has("version") && object.has("scopes")) {
            return readCurrent(object);
        }
        return readLegacy(object);
    }

    private static Decoded readCurrent(JsonObject root) throws IOException {
        int version = root.get("version").getAsInt();
        if (version > VERSION) {
            throw new IOException("Storage index version " + version + " is newer than supported " + VERSION);
        }
        JsonElement scopes = root.get("scopes");
        if (!scopes.isJsonObject()) {
            throw new IOException("Storage index scopes must be an object");
        }
        List<IndexRecord> records = new ArrayList<>();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> scope : scopes.getAsJsonObject().entrySet()) {
            if (!scope.getValue().isJsonArray()) {
                changed = true;
                continue;
            }
            for (JsonElement element : scope.getValue().getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    changed = true;
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("x") || !entry.has("y") || !entry.has("z")) {
                    changed = true;
                    continue;
                }
                Map<String, Integer> counts = new LinkedHashMap<>();
                JsonElement rawCounts = entry.get("c");
                if (rawCounts != null && rawCounts.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> count : rawCounts.getAsJsonObject().entrySet()) {
                        changed |= !addCount(counts, count.getKey(), intValue(count.getValue()));
                    }
                }
                JsonElement block = entry.get("b");
                JsonElement updated = entry.get("t");
                records.add(new IndexRecord(scope.getKey(), entry.get("x").getAsInt(), entry.get("y").getAsInt(),
                        entry.get("z").getAsInt(),
                        block == null || !block.isJsonPrimitive() ? null : block.getAsString(),
                        counts, updated == null ? 0L : updated.getAsLong()));
            }
        }
        return new Decoded(records, changed);
    }

    private static Decoded readLegacy(JsonObject root) {
        List<IndexRecord> records = new ArrayList<>();
        boolean changed = true;
        for (Map.Entry<String, JsonElement> value : root.entrySet()) {
            if (!value.getValue().isJsonObject()) {
                continue;
            }
            JsonObject entry = value.getValue().getAsJsonObject();
            JsonElement scope = entry.get("scope");
            if (scope == null || !scope.isJsonPrimitive() || !entry.has("x") || !entry.has("y") || !entry.has("z")) {
                continue;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            JsonElement rawCounts = entry.get("counts");
            if (rawCounts != null && rawCounts.isJsonObject()) {
                for (Map.Entry<String, JsonElement> count : rawCounts.getAsJsonObject().entrySet()) {
                    int amount = intValue(count.getValue());
                    if (amount > 0) {
                        addCount(counts, count.getKey(), amount);
                    }
                }
            }
            JsonElement items = entry.get("items");
            if (items == null || !items.isJsonArray()) {
                continue;
            }
            for (JsonElement item : items.getAsJsonArray()) {
                if (item.isJsonPrimitive() && ItemKeys.isValid(item.getAsString())) {
                    for (String key : ItemKeys.expandLegacy(item.getAsString())) {
                        counts.putIfAbsent(key, 0);
                    }
                }
            }
            JsonElement updated = entry.get("updatedAt");
            records.add(new IndexRecord(scope.getAsString(), entry.get("x").getAsInt(),
                    entry.get("y").getAsInt(), entry.get("z").getAsInt(), null, counts,
                    updated == null ? 0L : updated.getAsLong()));
        }
        return new Decoded(records, changed);
    }

    private static boolean addCount(Map<String, Integer> counts, String rawKey, int amount) {
        if (!ItemKeys.isValid(rawKey) || amount < 0) {
            return false;
        }
        boolean unchanged = true;
        for (String key : ItemKeys.expandLegacy(rawKey)) {
            unchanged &= key.equals(rawKey);
            counts.merge(key, amount, Math::max);
        }
        return unchanged;
    }

    private static int intValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        long value = element.getAsLong();
        return (int) Math.max(-1L, Math.min(Integer.MAX_VALUE, value));
    }

    public record Decoded(List<IndexRecord> records, boolean migrated) {
    }
}

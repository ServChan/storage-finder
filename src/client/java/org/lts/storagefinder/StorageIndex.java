package org.lts.storagefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class StorageIndex {
    private static final int MAX_RECORDS_PER_SCOPE = 10_000;
    private static final int MAX_RECORDS_TOTAL = 50_000;
    private static final long SAVE_DEBOUNCE_MILLIS = 750L;
    private static final long SAVE_RETRY_MILLIS = 5_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Record>>() { }.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("storage_finder_index.json");
    private static final Path BACKUP_PATH = PATH.resolveSibling(PATH.getFileName() + ".bak");
    private static final Path RECOVERY_PATH = PATH.resolveSibling(PATH.getFileName() + ".recovery");
    private static final Path EXPORT_DIRECTORY = PATH.resolveSibling("storage_finder_exports");
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "StorageFinder-IndexIO");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Record> records = new LinkedHashMap<>();
    private final Map<String, List<Record>> recordsByScope = new LinkedHashMap<>();
    private final Map<String, Integer> recordCountsByScope = new LinkedHashMap<>();
    private long revision;
    private long savedRevision;
    private boolean lastSaveFailed;
    private boolean saveFailureNotice;
    private boolean primaryExists;
    private long nextSaveAt;
    private long retryNotBefore;
    private CompletableFuture<SaveOutcome> pendingSave;
    private volatile int recoverySize = -1;

    public void load() {
        recoverySize = recoveryRecordCount();
        primaryExists = Files.exists(PATH);
        if (!primaryExists) {
            restoreBestRecoveryIfAvailable();
            return;
        }
        try {
            boolean migratedBookKeys = loadFrom(PATH);
            if (records.isEmpty() && restoreBestRecoveryIfAvailable()) {
                return;
            }
            if (migratedBookKeys) {
                markDirty(0L);
                if (saveNow(true)) {
                    StorageFinderClient.LOGGER.info("Migrated legacy enchanted-book search keys");
                }
            } else {
                updateRecoverySnapshot(records.size());
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not load storage index {}", PATH, exception);
            restoreBestRecoveryIfAvailable();
        }
    }

    public UpdateResult update(String scope, BlockPos pos, AbstractContainerMenu menu, int storageSlots) {
        Set<String> itemIds = new LinkedHashSet<>();
        Map<String, Integer> itemCounts = new LinkedHashMap<>();
        for (int slot = 0; slot < storageSlots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) {
                for (String itemId : SearchSelection.itemIds(stack)) {
                    itemIds.add(itemId);
                    itemCounts.merge(itemId, stack.getCount(), Integer::sum);
                }
            }
        }

        String key = key(scope, pos);
        Record previous = records.get(key);
        UpdateKind kind = previous == null ? UpdateKind.NEW
                : previous.items.equals(itemIds) && previous.normalizedCounts().equals(itemCounts)
                ? UpdateKind.UNCHANGED : UpdateKind.UPDATED;
        if (kind != UpdateKind.UNCHANGED || !primaryExists && !isDirty()) {
            Record record = new Record(scope, pos.getX(), pos.getY(), pos.getZ(), itemIds,
                    itemCounts, System.currentTimeMillis());
            records.put(key, record);
            recordsByScope.remove(scope);
            if (previous == null) {
                recordCountsByScope.merge(scope, 1, Integer::sum);
            }
            markDirty(SAVE_DEBOUNCE_MILLIS);
            pruneIfNeeded();
        }
        return new UpdateResult(kind, itemIds.size(), !lastSaveFailed);
    }

    public Set<String> itemsAt(String scope, BlockPos pos) {
        Record record = records.get(key(scope, pos));
        return record == null ? Set.of() : Set.copyOf(record.items);
    }

    public void tick() {
        collectFinishedSave();
        long now = System.currentTimeMillis();
        if (isDirty() && pendingSave == null && now >= nextSaveAt && now >= retryNotBefore) {
            scheduleSave();
        }
    }

    public boolean flush() {
        collectFinishedSave();
        if (isDirty() && pendingSave == null && System.currentTimeMillis() >= retryNotBefore) {
            scheduleSave();
        } else if (isDirty()) {
            nextSaveAt = 0L;
        }
        return !lastSaveFailed;
    }

    public boolean flushBlocking() {
        if (pendingSave != null) {
            pendingSave.join();
            collectFinishedSave();
        }
        return !isDirty() || saveNow(true);
    }

    public boolean consumeSaveFailureNotice() {
        collectFinishedSave();
        boolean notice = saveFailureNotice;
        saveFailureNotice = false;
        return notice;
    }

    public Collection<Record> recordsInScope(String scope) {
        return recordsByScope.computeIfAbsent(scope, requestedScope -> records.values().stream()
                .filter(record -> requestedScope.equals(record.scope))
                .toList());
    }

    public List<RecordView> recordViews() {
        long now = System.currentTimeMillis();
        return records.entrySet().stream()
                .map(entry -> {
                    Record record = entry.getValue();
                    long total = record.normalizedCounts().values().stream()
                            .mapToLong(Integer::longValue).sum();
                    return new RecordView(entry.getKey(), record.scope, record.pos(), record.items.size(),
                            (int) Math.min(Integer.MAX_VALUE, total), record.updatedAt,
                            IndexFreshness.classify(record.updatedAt, now));
                })
                .toList();
    }

    public boolean removeRecord(String recordKey) {
        Record removed = records.remove(recordKey);
        if (removed == null) {
            return false;
        }
        recordsByScope.remove(removed.scope);
        rebuildScopeCounts();
        markDirty(0L);
        flush();
        return true;
    }

    public int removeScope(String scope) {
        int before = records.size();
        records.entrySet().removeIf(entry -> scope.equals(entry.getValue().scope));
        int removed = before - records.size();
        if (removed == 0) {
            return 0;
        }
        recordsByScope.remove(scope);
        rebuildScopeCounts();
        markDirty(0L);
        flush();
        return removed;
    }

    public void exportSnapshot(Consumer<Path> onComplete) {
        Map<String, Record> snapshot = new LinkedHashMap<>(records);
        Path target = EXPORT_DIRECTORY.resolve(
                "storage_finder_index_" + EXPORT_TIME.format(Instant.now()) + ".json");
        CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(EXPORT_DIRECTORY);
                try (Writer writer = Files.newBufferedWriter(target)) {
                    GSON.toJson(snapshot, DATA_TYPE, writer);
                }
                return target;
            } catch (Exception exception) {
                StorageFinderClient.LOGGER.warn("Could not export storage index", exception);
                return null;
            }
        }, IO).thenAccept(result -> Minecraft.getInstance().execute(() -> onComplete.accept(result)));
    }

    public void migrateLegacyScopeIfNearby(String scopedWorld, BlockPos playerPos) {
        int seedMarker = scopedWorld.lastIndexOf("|seed:");
        if (seedMarker < 0) {
            return;
        }
        String legacyScope = scopedWorld.substring(0, seedMarker);
        if (!recordCountsByScope.containsKey(legacyScope)) {
            return;
        }
        List<Map.Entry<String, Record>> legacyEntries = new ArrayList<>();
        for (Map.Entry<String, Record> entry : records.entrySet()) {
            Record record = entry.getValue();
            if (legacyScope.equals(record.scope)
                    && StorageLocator.withinSearchRadius(playerPos, record.pos())) {
                legacyEntries.add(entry);
            }
        }
        if (legacyEntries.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Record> entry : legacyEntries) {
            Record legacy = entry.getValue();
            records.remove(entry.getKey());
            Record migrated = new Record(scopedWorld, legacy.x, legacy.y, legacy.z, legacy.items,
                    legacy.normalizedCounts(), legacy.updatedAt);
            String newKey = key(scopedWorld, migrated.pos());
            Record current = records.get(newKey);
            if (current == null || current.updatedAt < migrated.updatedAt) {
                records.put(newKey, migrated);
            }
        }
        recordsByScope.remove(legacyScope);
        recordsByScope.remove(scopedWorld);
        rebuildScopeCounts();
        markDirty(0L);
        StorageFinderClient.LOGGER.info("Migrated {} nearby legacy storage records to world-scoped index",
                legacyEntries.size());
    }

    public static BlockPos canonicalPos(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return pos.immutable();
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            return compare(pos, other) <= 0 ? pos.immutable() : other.immutable();
        }
        return pos.immutable();
    }

    public static List<BlockPos> physicalBlocks(Minecraft minecraft, BlockPos canonical) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(canonical);
        if (minecraft.level == null) {
            return positions;
        }
        BlockState state = minecraft.level.getBlockState(canonical);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            positions.add(ChestBlock.getConnectedBlockPos(canonical, state));
        }
        return positions;
    }

    private boolean isDirty() {
        return revision != savedRevision;
    }

    private void markDirty(long debounceMillis) {
        revision++;
        nextSaveAt = debounceMillis <= 0L ? 0L
                : Math.max(nextSaveAt, System.currentTimeMillis() + debounceMillis);
    }

    private void scheduleSave() {
        long snapshotRevision = revision;
        Map<String, Record> snapshot = new LinkedHashMap<>(records);
        pendingSave = CompletableFuture.supplyAsync(
                () -> new SaveOutcome(snapshotRevision, writeSnapshot(snapshot, true)), IO);
    }

    private void collectFinishedSave() {
        if (pendingSave == null || !pendingSave.isDone()) {
            return;
        }
        SaveOutcome outcome = pendingSave.join();
        pendingSave = null;
        applyOutcome(outcome);
    }

    private void applyOutcome(SaveOutcome outcome) {
        if (outcome.success()) {
            savedRevision = Math.max(savedRevision, outcome.revision());
            primaryExists = true;
            lastSaveFailed = false;
            retryNotBefore = 0L;
        } else {
            if (!lastSaveFailed) {
                saveFailureNotice = true;
            }
            lastSaveFailed = true;
            retryNotBefore = System.currentTimeMillis() + SAVE_RETRY_MILLIS;
        }
    }

    private boolean saveNow(boolean backupCurrent) {
        long snapshotRevision = revision;
        SaveOutcome outcome = new SaveOutcome(snapshotRevision,
                writeSnapshot(new LinkedHashMap<>(records), backupCurrent));
        applyOutcome(outcome);
        return outcome.success();
    }

    private boolean writeSnapshot(Map<String, Record> snapshot, boolean backupCurrent) {
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(snapshot, DATA_TYPE, writer);
            }
            if (backupCurrent && Files.exists(PATH)) {
                try {
                    Files.copy(PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception exception) {
                    StorageFinderClient.LOGGER.warn("Could not update storage index backup {}", BACKUP_PATH, exception);
                }
            }
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            updateRecoverySnapshot(snapshot.size());
            return true;
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not save storage index {}", PATH, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private boolean loadFrom(Path source) throws IOException {
        Map<String, Record> loaded;
        try (Reader reader = Files.newBufferedReader(source)) {
            loaded = GSON.fromJson(reader, DATA_TYPE);
        }
        if (loaded == null) {
            throw new IOException("Storage index contains JSON null: " + source);
        }

        Map<String, Record> validated = new LinkedHashMap<>();
        boolean migratedBookKeys = false;
        for (Record value : loaded.values()) {
            if (value == null || value.scope == null || value.items == null) {
                continue;
            }
            Set<String> normalizedItems = normalizeItemKeys(value.items);
            if (!normalizedItems.equals(value.items)) {
                value.items = normalizedItems;
                migratedBookKeys = true;
            }
            Map<String, Integer> normalizedCounts = normalizeCounts(value.normalizedCounts());
            if (!normalizedCounts.equals(value.normalizedCounts())) {
                value.counts = normalizedCounts;
                migratedBookKeys = true;
            }
            validated.put(key(value.scope, value.pos()), value);
        }
        records.clear();
        records.putAll(validated);
        recordsByScope.clear();
        rebuildScopeCounts();
        boolean pruned = pruneIfNeeded() > 0;
        return migratedBookKeys || pruned;
    }

    private boolean restoreBestRecoveryIfAvailable() {
        Map<String, Record> bestRecords = Map.of();
        Path bestSource = null;
        boolean bestMigrated = false;
        for (Path candidate : List.of(BACKUP_PATH, RECOVERY_PATH)) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                boolean migrated = loadFrom(candidate);
                if (records.size() > bestRecords.size()) {
                    bestRecords = new LinkedHashMap<>(records);
                    bestSource = candidate;
                    bestMigrated = migrated;
                }
            } catch (Exception exception) {
                StorageFinderClient.LOGGER.warn("Could not read storage recovery candidate {}", candidate, exception);
            }
        }
        if (bestSource == null || bestRecords.isEmpty()) {
            records.clear();
            return false;
        }
        records.clear();
        records.putAll(bestRecords);
        recordsByScope.clear();
        rebuildScopeCounts();
        markDirty(0L);
        if (!saveNow(false)) {
            return false;
        }
        StorageFinderClient.LOGGER.info("Restored {} storage records from {}{}", records.size(), bestSource,
                bestMigrated ? " and migrated legacy enchanted-book keys" : "");
        return true;
    }

    private void updateRecoverySnapshot(int recordCount) {
        try {
            if (recordCount > recoverySize && Files.exists(PATH)) {
                Files.copy(PATH, RECOVERY_PATH, StandardCopyOption.REPLACE_EXISTING);
                recoverySize = recordCount;
                StorageFinderClient.LOGGER.info("Updated storage recovery snapshot: {} records", recordCount);
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not update storage recovery snapshot {}", RECOVERY_PATH, exception);
        }
    }

    private int recoveryRecordCount() {
        if (!Files.exists(RECOVERY_PATH)) {
            return -1;
        }
        try (Reader reader = Files.newBufferedReader(RECOVERY_PATH)) {
            Map<String, Record> recovery = GSON.fromJson(reader, DATA_TYPE);
            return recovery == null ? -1 : recovery.size();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int pruneIfNeeded() {
        boolean scopeOverLimit = recordCountsByScope.values().stream()
                .anyMatch(count -> count > MAX_RECORDS_PER_SCOPE);
        if (records.size() <= MAX_RECORDS_TOTAL && !scopeOverLimit) {
            return 0;
        }
        List<IndexRetentionPolicy.Entry> candidates = records.entrySet().stream()
                .map(entry -> new IndexRetentionPolicy.Entry(entry.getKey(), entry.getValue().scope,
                        entry.getValue().updatedAt))
                .toList();
        Set<String> removals = IndexRetentionPolicy.selectRemovals(
                candidates, MAX_RECORDS_PER_SCOPE, MAX_RECORDS_TOTAL);
        if (removals.isEmpty()) {
            return 0;
        }
        removals.forEach(records::remove);
        recordsByScope.clear();
        rebuildScopeCounts();
        markDirty(SAVE_DEBOUNCE_MILLIS);
        StorageFinderClient.LOGGER.warn("Pruned {} oldest storage records; retained {}",
                removals.size(), records.size());
        return removals.size();
    }

    private void rebuildScopeCounts() {
        recordCountsByScope.clear();
        records.values().forEach(record -> recordCountsByScope.merge(record.scope, 1, Integer::sum));
    }

    private static String key(String scope, BlockPos pos) {
        return scope + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Set<String> normalizeItemKeys(Collection<String> itemKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String itemKey : itemKeys) {
            if (itemKey == null || itemKey.isBlank() || itemKey.length() > 512) {
                continue;
            }
            normalized.addAll(expandLegacyItemKey(itemKey));
        }
        return normalized;
    }

    private static Map<String, Integer> normalizeCounts(Map<String, Integer> counts) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String itemKey = entry.getKey();
            Integer count = entry.getValue();
            if (itemKey == null || itemKey.isBlank() || itemKey.length() > 512
                    || count == null || count <= 0) {
                continue;
            }
            for (String expanded : expandLegacyItemKey(itemKey)) {
                normalized.merge(expanded, count, Math::max);
            }
        }
        return normalized;
    }

    private static Set<String> expandLegacyItemKey(String itemKey) {
        Set<String> normalized = new LinkedHashSet<>();
        String marker = "|stored_enchantments=";
        int markerIndex = itemKey.indexOf(marker);
        if (markerIndex < 0) {
            normalized.add(itemKey);
            return normalized;
        }
        String itemId = itemKey.substring(0, markerIndex);
        String enchantments = itemKey.substring(markerIndex + marker.length());
        if (enchantments.isEmpty()) {
            normalized.add(itemId + "|stored_enchantment=");
        } else {
            for (String enchantment : enchantments.split(",")) {
                if (!enchantment.isBlank()) {
                    normalized.add(itemId + "|stored_enchantment=" + enchantment);
                }
            }
        }
        return normalized;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    public static final class Record {
        public String scope;
        public int x;
        public int y;
        public int z;
        public Set<String> items;
        public Map<String, Integer> counts;
        public long updatedAt;

        public Record(String scope, int x, int y, int z, Set<String> items,
                      Map<String, Integer> counts, long updatedAt) {
            this.scope = scope;
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = new LinkedHashSet<>(items);
            this.counts = new LinkedHashMap<>(counts);
            this.updatedAt = updatedAt;
        }

        public Map<String, Integer> normalizedCounts() {
            return counts == null ? Map.of() : counts;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    public enum UpdateKind {
        NEW,
        UPDATED,
        UNCHANGED
    }

    public record UpdateResult(UpdateKind kind, int itemTypes, boolean persisted) {
    }

    private record SaveOutcome(long revision, boolean success) {
    }

    public record RecordView(String key, String scope, BlockPos pos, int itemTypes,
                             int itemCount, long updatedAt, IndexFreshness freshness) {
    }
}

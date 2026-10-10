package org.lts.storagefinder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
    private final RecordStore store = new RecordStore();
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
            boolean migrated = loadFrom(PATH);
            if (store.isEmpty() && restoreBestRecoveryIfAvailable()) {
                return;
            }
            if (migrated) {
                markDirty(0L);
                if (saveNow(true)) {
                    StorageFinderClient.LOGGER.info("Migrated storage index to format {}", IndexCodec.VERSION);
                }
            } else {
                updateRecoverySnapshot(store.size());
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not read storage index {}; trying recovery copies", PATH, exception);
            restoreBestRecoveryIfAvailable();
        }
    }

    public UpdateResult update(String scope, BlockPos canonical, Collection<BlockPos> physical, String family,
                               Map<String, Integer> counts) {
        IndexRecord previous = store.get(scope, canonical.getX(), canonical.getY(), canonical.getZ());
        UpdateKind kind = previous == null ? UpdateKind.NEW
                : previous.counts().equals(counts) && family != null && family.equals(previous.block())
                ? UpdateKind.UNCHANGED : UpdateKind.UPDATED;
        boolean removedDuplicates = false;
        for (BlockPos other : physical) {
            if (!other.equals(canonical)) {
                removedDuplicates |= store.remove(
                        RecordStore.key(scope, other.getX(), other.getY(), other.getZ())) != null;
            }
        }
        if (kind != UpdateKind.UNCHANGED || removedDuplicates || !primaryExists && !isDirty()) {
            store.put(new IndexRecord(scope, canonical.getX(), canonical.getY(), canonical.getZ(), family,
                    counts, System.currentTimeMillis()));
            markDirty(SAVE_DEBOUNCE_MILLIS);
            pruneIfNeeded();
        }
        return new UpdateResult(kind, (int) counts.keySet().stream().filter(key -> !ItemKeys.isNameKey(key)).count(),
                !lastSaveFailed);
    }

    public IndexRecord get(String scope, BlockPos pos) {
        return store.get(scope, pos.getX(), pos.getY(), pos.getZ());
    }

    public List<IndexRecord> near(String scope, BlockPos center, int chunkRadius) {
        return store.near(scope, center.getX() >> 4, center.getZ() >> 4, chunkRadius);
    }

    public void forEachInScope(String scope, Consumer<IndexRecord> consumer) {
        store.forEachInScope(scope, consumer);
    }

    public long revision() {
        return store.revision();
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

    public List<RecordView> recordViews() {
        long now = System.currentTimeMillis();
        List<RecordView> views = new ArrayList<>(store.size());
        for (IndexRecord record : store.all()) {
            long total = record.totalCount();
            int types = (int) record.items().stream().filter(key -> !ItemKeys.isNameKey(key)).count();
            views.add(new RecordView(record.key(), record.scope(), pos(record), record.block(), types,
                    (int) Math.min(Integer.MAX_VALUE, total), record.updatedAt(),
                    IndexFreshness.classify(record.updatedAt(), now)));
        }
        return views;
    }

    public boolean removeRecord(String recordKey) {
        if (store.remove(recordKey) == null) {
            return false;
        }
        markDirty(0L);
        flush();
        return true;
    }

    public int removeRecords(Collection<String> keys) {
        int removed = store.removeAll(keys);
        if (removed > 0) {
            markDirty(SAVE_DEBOUNCE_MILLIS);
            StorageFinderClient.LOGGER.debug("Forgot {} storage records whose blocks are gone", removed);
        }
        return removed;
    }

    public int removeScope(String scope) {
        int removed = store.removeScope(scope);
        if (removed > 0) {
            markDirty(0L);
            flush();
        }
        return removed;
    }

    public void exportSnapshot(Consumer<Path> onComplete) {
        List<IndexRecord> snapshot = store.snapshot();
        Path target = EXPORT_DIRECTORY.resolve(
                "storage_finder_index_" + EXPORT_TIME.format(Instant.now()) + ".json");
        CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(EXPORT_DIRECTORY);
                try (Writer writer = Files.newBufferedWriter(target)) {
                    IndexCodec.write(snapshot, writer, true);
                }
                return target;
            } catch (Exception exception) {
                StorageFinderClient.LOGGER.warn("Could not export the storage index to {}", target, exception);
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
        if (!store.hasScope(legacyScope)) {
            return;
        }
        List<IndexRecord> legacyRecords = new ArrayList<>();
        for (IndexRecord record : store.near(legacyScope, playerPos.getX() >> 4, playerPos.getZ() >> 4,
                StorageFinderConfig.MAX_SEARCH_CHUNKS + 1)) {
            if (StorageLocator.withinSearchRadius(playerPos, pos(record))) {
                legacyRecords.add(record);
            }
        }
        if (legacyRecords.isEmpty()) {
            return;
        }
        for (IndexRecord legacy : legacyRecords) {
            store.remove(legacy.key());
            IndexRecord current = store.get(scopedWorld, legacy.x(), legacy.y(), legacy.z());
            if (current == null || current.updatedAt() < legacy.updatedAt()) {
                store.put(legacy.inScope(scopedWorld));
            }
        }
        markDirty(0L);
        StorageFinderClient.LOGGER.info("Migrated {} nearby legacy storage records to the world-scoped index",
                legacyRecords.size());
    }

    public static BlockPos pos(IndexRecord record) {
        return new BlockPos(record.x(), record.y(), record.z());
    }

    public static BlockPos canonicalPos(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return pos.immutable();
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (isDoubleChest(state)) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            return compare(pos, other) <= 0 ? pos.immutable() : other.immutable();
        }
        return pos.immutable();
    }

    public static List<BlockPos> physicalBlocks(Minecraft minecraft, BlockPos canonical) {
        List<BlockPos> positions = new ArrayList<>(2);
        positions.add(canonical);
        if (minecraft.level == null) {
            return positions;
        }
        BlockState state = minecraft.level.getBlockState(canonical);
        if (isDoubleChest(state)) {
            positions.add(ChestBlock.getConnectedBlockPos(canonical, state));
        }
        return positions;
    }

    private static boolean isDoubleChest(BlockState state) {
        return state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
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
        List<IndexRecord> snapshot = store.snapshot();
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
        SaveOutcome outcome = new SaveOutcome(revision, writeSnapshot(store.snapshot(), backupCurrent));
        applyOutcome(outcome);
        return outcome.success();
    }

    private boolean writeSnapshot(List<IndexRecord> snapshot, boolean backupCurrent) {
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                IndexCodec.write(snapshot, writer, false);
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
        IndexCodec.Decoded decoded;
        try (Reader reader = Files.newBufferedReader(source)) {
            decoded = IndexCodec.read(reader);
        }
        store.replaceAll(decoded.records());
        boolean pruned = pruneIfNeeded() > 0;
        return decoded.migrated() || pruned;
    }

    private boolean restoreBestRecoveryIfAvailable() {
        List<IndexRecord> bestRecords = List.of();
        Path bestSource = null;
        for (Path candidate : List.of(BACKUP_PATH, RECOVERY_PATH)) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                loadFrom(candidate);
                if (store.size() > bestRecords.size()) {
                    bestRecords = store.snapshot();
                    bestSource = candidate;
                }
            } catch (Exception exception) {
                StorageFinderClient.LOGGER.warn("Could not read storage recovery candidate {}", candidate, exception);
            }
        }
        if (bestSource == null || bestRecords.isEmpty()) {
            store.replaceAll(List.of());
            return false;
        }
        store.replaceAll(bestRecords);
        markDirty(0L);
        if (!saveNow(false)) {
            return false;
        }
        StorageFinderClient.LOGGER.info("Restored {} storage records from {}", store.size(), bestSource);
        return true;
    }

    private void updateRecoverySnapshot(int recordCount) {
        try {
            if (recordCount > recoverySize && Files.exists(PATH)) {
                Files.copy(PATH, RECOVERY_PATH, StandardCopyOption.REPLACE_EXISTING);
                recoverySize = recordCount;
                StorageFinderClient.LOGGER.debug("Updated storage recovery snapshot with {} records", recordCount);
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
            return IndexCodec.read(reader).records().size();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int pruneIfNeeded() {
        boolean scopeOverLimit = store.scopeCounts().values().stream()
                .anyMatch(count -> count > MAX_RECORDS_PER_SCOPE);
        if (store.size() <= MAX_RECORDS_TOTAL && !scopeOverLimit) {
            return 0;
        }
        List<IndexRetentionPolicy.Entry> candidates = store.all().stream()
                .map(record -> new IndexRetentionPolicy.Entry(record.key(), record.scope(), record.updatedAt()))
                .toList();
        Set<String> removals = IndexRetentionPolicy.selectRemovals(
                candidates, MAX_RECORDS_PER_SCOPE, MAX_RECORDS_TOTAL);
        if (removals.isEmpty()) {
            return 0;
        }
        store.removeAll(removals);
        markDirty(SAVE_DEBOUNCE_MILLIS);
        StorageFinderClient.LOGGER.info("Pruned {} oldest storage records; {} kept",
                removals.size(), store.size());
        return removals.size();
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
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

    public record RecordView(String key, String scope, BlockPos pos, String block, int itemTypes,
                             int itemCount, long updatedAt, IndexFreshness freshness) {
    }
}

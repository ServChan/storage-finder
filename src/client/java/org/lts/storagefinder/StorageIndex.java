package org.lts.storagefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StorageIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Record>>() { }.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("storage_finder_index.json");
    private static final Path BACKUP_PATH = PATH.resolveSibling(PATH.getFileName() + ".bak");
    private final Map<String, Record> records = new LinkedHashMap<>();
    private boolean dirty;

    public void load() {
        if (!Files.exists(PATH)) {
            restoreBackupIfAvailable();
            return;
        }
        try {
            boolean migratedBookKeys = loadFrom(PATH);
            if (records.isEmpty() && restoreBackupIfAvailable()) {
                return;
            }
            dirty = migratedBookKeys;
            if (migratedBookKeys) {
                if (save(true)) {
                    StorageFinderClient.LOGGER.info("Migrated legacy enchanted-book search keys");
                }
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not load storage index {}", PATH, exception);
            restoreBackupIfAvailable();
        }
    }

    public UpdateResult update(String scope, BlockPos pos, ChestMenu menu) {
        Set<String> itemIds = new LinkedHashSet<>();
        Container container = menu.getContainer();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                itemIds.addAll(SearchSelection.itemIds(stack));
            }
        }

        String key = key(scope, pos);
        Record previous = records.get(key);
        UpdateKind kind = previous == null ? UpdateKind.NEW
                : previous.items.equals(itemIds) ? UpdateKind.UNCHANGED : UpdateKind.UPDATED;
        if (kind != UpdateKind.UNCHANGED || !Files.exists(PATH)) {
            Record record = new Record(scope, pos.getX(), pos.getY(), pos.getZ(), itemIds, System.currentTimeMillis());
            records.put(key, record);
            dirty = true;
        }
        boolean persisted = !dirty || save(true);
        return new UpdateResult(kind, itemIds.size(), persisted);
    }

    public Collection<Record> recordsInScope(String scope) {
        List<Record> result = new ArrayList<>();
        for (Record record : records.values()) {
            if (scope.equals(record.scope)) {
                result.add(record);
            }
        }
        return result;
    }

    public void migrateLegacyScopeIfNearby(String scopedWorld, BlockPos playerPos) {
        int seedMarker = scopedWorld.lastIndexOf("|seed:");
        if (seedMarker < 0) {
            return;
        }
        String legacyScope = scopedWorld.substring(0, seedMarker);
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
            Record record = entry.getValue();
            records.remove(entry.getKey());
            record.scope = scopedWorld;
            String newKey = key(scopedWorld, record.pos());
            Record current = records.get(newKey);
            if (current == null || current.updatedAt < record.updatedAt) {
                records.put(newKey, record);
            }
        }
        dirty = true;
        if (save(true)) {
            StorageFinderClient.LOGGER.info("Migrated {} nearby legacy storage records to world-scoped index",
                    legacyEntries.size());
        }
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

    private boolean save(boolean backupCurrent) {
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(records, DATA_TYPE, writer);
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
            dirty = false;
            return true;
        } catch (Exception exception) {
            dirty = true;
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
            validated.put(key(value.scope, value.pos()), value);
        }
        records.clear();
        records.putAll(validated);
        return migratedBookKeys;
    }

    private boolean restoreBackupIfAvailable() {
        if (!Files.exists(BACKUP_PATH)) {
            return false;
        }
        try {
            boolean migratedBookKeys = loadFrom(BACKUP_PATH);
            if (records.isEmpty()) {
                return false;
            }
            dirty = true;
            if (save(false)) {
                StorageFinderClient.LOGGER.info("Restored storage index from {}{}", BACKUP_PATH,
                        migratedBookKeys ? " and migrated legacy enchanted-book keys" : "");
                return true;
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not restore storage index backup {}", BACKUP_PATH, exception);
        }
        return false;
    }

    private static String key(String scope, BlockPos pos) {
        return scope + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Set<String> normalizeItemKeys(Collection<String> itemKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String itemKey : itemKeys) {
            String marker = "|stored_enchantments=";
            int markerIndex = itemKey.indexOf(marker);
            if (markerIndex < 0) {
                normalized.add(itemKey);
                continue;
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
        public long updatedAt;

        public Record(String scope, int x, int y, int z, Set<String> items, long updatedAt) {
            this.scope = scope;
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = new LinkedHashSet<>(items);
            this.updatedAt = updatedAt;
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
}

package org.lts.storagefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StorageFinderConfig {
    public static final int MAX_SEARCH_CHUNKS = 8;
    public static final int MAX_HUD_OFFSET = 1_000;
    public static final String COMPLETE_ON_TAKE = "TAKEN";
    public static final String COMPLETE_ON_OPEN = "OPENED";
    public static final String COMPLETE_NEVER = "NEVER";
    public static final java.util.List<String> COMPLETION_MODES =
            java.util.List.of(COMPLETE_ON_TAKE, COMPLETE_ON_OPEN, COMPLETE_NEVER);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("storage_finder.json");
    private static final Path BACKUP_PATH = PATH.resolveSibling("storage_finder.json.bak");
    private static StorageFinderConfig instance = defaults();
    private static boolean primaryValid = true;
    private static long lastModified = -1L;
    private static long nextCheckTime;

    public boolean enabled = true;
    public boolean indexContainers = true;
    public boolean searchItemFrames = true;
    public boolean searchRememberedContents = true;
    public boolean highlightEnabled = true;
    public boolean routeEnabled = true;
    public boolean avoidHazards = true;
    public boolean routeAllowSwimming = true;
    public boolean routeThroughDoors = true;
    public boolean showMessages = true;
    public boolean shulkerPreview = true;
    public String searchCompletion = COMPLETE_ON_TAKE;
    public int routeMaxRoutes = 3;
    public int routeNodeBudget = 800;
    public int searchRadiusChunks = MAX_SEARCH_CHUNKS;
    public String hudAnchor = "BOTTOM_RIGHT";
    public int hudOffsetX = 0;
    public int hudOffsetY = 48;
    public boolean hudCompact = false;
    public boolean hudShowDistance = true;
    public boolean hudShowFreshness = true;
    public int hudMaxVisible = 6;
    public int hudOpacityPercent = 72;

    public static StorageFinderConfig load() {
        ensureExists();
        try (Reader reader = Files.newBufferedReader(PATH)) {
            StorageFinderConfig loaded = GSON.fromJson(reader, StorageFinderConfig.class);
            instance = sanitize(loaded == null ? defaults() : loaded);
            primaryValid = true;
            lastModified = Files.getLastModifiedTime(PATH).toMillis();
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not read {}; trying the backup", PATH, exception);
            primaryValid = false;
            instance = loadBackup();
            lastModified = modifiedTime();
        }
        return instance;
    }

    public static StorageFinderConfig loadIfChanged() {
        long now = System.currentTimeMillis();
        if (now < nextCheckTime) {
            return instance;
        }
        nextCheckTime = now + 1_000L;
        long modified = modifiedTime();
        if (modified != lastModified) {
            return load();
        }
        return instance;
    }

    public static StorageFinderConfig current() {
        return instance;
    }

    public static StorageFinderConfig currentCopy() {
        return instance.copy();
    }

    public static StorageFinderConfig defaultsCopy() {
        return defaults();
    }

    public static boolean save(StorageFinderConfig config) {
        StorageFinderConfig sanitized = sanitize(config.copy());
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(sanitized, writer);
            }
            if (primaryValid && Files.isRegularFile(PATH)) {
                Files.copy(PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            instance = sanitized;
            primaryValid = true;
            lastModified = Files.getLastModifiedTime(PATH).toMillis();
            return true;
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not save {}", PATH, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    public double radiusBlocks() {
        return searchRadiusChunks * 16.0;
    }

    public boolean sameAs(StorageFinderConfig other) {
        return other != null && GSON.toJson(this).equals(GSON.toJson(other));
    }

    private StorageFinderConfig copy() {
        StorageFinderConfig copy = new StorageFinderConfig();
        copy.enabled = enabled;
        copy.indexContainers = indexContainers;
        copy.searchItemFrames = searchItemFrames;
        copy.searchRememberedContents = searchRememberedContents;
        copy.highlightEnabled = highlightEnabled;
        copy.routeEnabled = routeEnabled;
        copy.avoidHazards = avoidHazards;
        copy.routeAllowSwimming = routeAllowSwimming;
        copy.routeThroughDoors = routeThroughDoors;
        copy.showMessages = showMessages;
        copy.shulkerPreview = shulkerPreview;
        copy.searchCompletion = searchCompletion;
        copy.routeMaxRoutes = routeMaxRoutes;
        copy.routeNodeBudget = routeNodeBudget;
        copy.searchRadiusChunks = searchRadiusChunks;
        copy.hudAnchor = hudAnchor;
        copy.hudOffsetX = hudOffsetX;
        copy.hudOffsetY = hudOffsetY;
        copy.hudCompact = hudCompact;
        copy.hudShowDistance = hudShowDistance;
        copy.hudShowFreshness = hudShowFreshness;
        copy.hudMaxVisible = hudMaxVisible;
        copy.hudOpacityPercent = hudOpacityPercent;
        return copy;
    }

    private static StorageFinderConfig defaults() {
        return new StorageFinderConfig();
    }

    private static StorageFinderConfig sanitize(StorageFinderConfig config) {
        if (config == null) {
            return defaults();
        }
        config.searchRadiusChunks = Math.max(1, Math.min(MAX_SEARCH_CHUNKS, config.searchRadiusChunks));
        if (!java.util.Set.of("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
                .contains(config.hudAnchor)) {
            config.hudAnchor = "BOTTOM_RIGHT";
        }
        if (!COMPLETION_MODES.contains(config.searchCompletion)) {
            config.searchCompletion = COMPLETE_ON_TAKE;
        }
        config.hudOffsetX = Math.max(0, Math.min(MAX_HUD_OFFSET, config.hudOffsetX));
        config.hudOffsetY = Math.max(0, Math.min(MAX_HUD_OFFSET, config.hudOffsetY));
        config.hudMaxVisible = Math.max(1, Math.min(12, config.hudMaxVisible));
        config.hudOpacityPercent = Math.max(30, Math.min(100, config.hudOpacityPercent));
        config.routeMaxRoutes = Math.max(1, Math.min(5, config.routeMaxRoutes));
        config.routeNodeBudget = Math.max(200, Math.min(2_000, config.routeNodeBudget));
        return config;
    }

    private static void ensureExists() {
        if (!Files.exists(PATH)) {
            save(defaults());
        }
    }

    private static StorageFinderConfig loadBackup() {
        if (!Files.isRegularFile(BACKUP_PATH)) {
            StorageFinderConfig recovered = defaults();
            quarantineCorruptPrimary();
            save(recovered);
            return recovered;
        }
        try (Reader reader = Files.newBufferedReader(BACKUP_PATH)) {
            StorageFinderConfig recovered = sanitize(GSON.fromJson(reader, StorageFinderConfig.class));
            StorageFinderClient.LOGGER.warn("Recovered the configuration from {}", BACKUP_PATH);
            try {
                if (Files.isRegularFile(PATH)) {
                    Files.move(PATH, PATH.resolveSibling("storage_finder.json.corrupt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                Files.copy(BACKUP_PATH, PATH, StandardCopyOption.REPLACE_EXISTING);
                primaryValid = true;
            } catch (Exception exception) {
                StorageFinderClient.LOGGER.warn("Could not restore {} from the backup", PATH, exception);
            }
            return recovered;
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not recover the configuration from {}; using defaults", BACKUP_PATH, exception);
            StorageFinderConfig recovered = defaults();
            quarantineCorruptPrimary();
            save(recovered);
            return recovered;
        }
    }

    private static void quarantineCorruptPrimary() {
        try {
            if (Files.isRegularFile(PATH)) {
                Files.move(PATH, PATH.resolveSibling("storage_finder.json.corrupt"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not move the corrupt {} aside", PATH, exception);
        }
    }

    private static long modifiedTime() {
        try {
            return Files.exists(PATH) ? Files.getLastModifiedTime(PATH).toMillis() : -1L;
        } catch (Exception ignored) {
            return -1L;
        }
    }
}

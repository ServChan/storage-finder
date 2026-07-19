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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("storage_finder.json");
    private static StorageFinderConfig instance = defaults();
    private static long lastModified = -1L;
    private static long nextCheckTime;

    public boolean enabled = true;
    public boolean indexContainers = true;
    public boolean searchItemFrames = true;
    public boolean searchRememberedContents = true;
    public boolean highlightEnabled = true;
    public boolean routeEnabled = true;
    public boolean avoidHazards = true;
    public boolean showMessages = true;
    public int searchRadiusChunks = MAX_SEARCH_CHUNKS;
    public String hudAnchor = "BOTTOM_RIGHT";
    public int hudOffsetX = 0;
    public int hudOffsetY = 48;

    public static StorageFinderConfig load() {
        ensureExists();
        try (Reader reader = Files.newBufferedReader(PATH)) {
            StorageFinderConfig loaded = GSON.fromJson(reader, StorageFinderConfig.class);
            instance = sanitize(loaded == null ? defaults() : loaded);
            lastModified = Files.getLastModifiedTime(PATH).toMillis();
        } catch (Exception exception) {
            StorageFinderClient.LOGGER.warn("Could not load {}", PATH, exception);
            instance = defaults();
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
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            instance = sanitized;
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

    private StorageFinderConfig copy() {
        StorageFinderConfig copy = new StorageFinderConfig();
        copy.enabled = enabled;
        copy.indexContainers = indexContainers;
        copy.searchItemFrames = searchItemFrames;
        copy.searchRememberedContents = searchRememberedContents;
        copy.highlightEnabled = highlightEnabled;
        copy.routeEnabled = routeEnabled;
        copy.avoidHazards = avoidHazards;
        copy.showMessages = showMessages;
        copy.searchRadiusChunks = searchRadiusChunks;
        copy.hudAnchor = hudAnchor;
        copy.hudOffsetX = hudOffsetX;
        copy.hudOffsetY = hudOffsetY;
        return copy;
    }

    private static StorageFinderConfig defaults() {
        return new StorageFinderConfig();
    }

    private static StorageFinderConfig sanitize(StorageFinderConfig config) {
        config.searchRadiusChunks = Math.max(1, Math.min(MAX_SEARCH_CHUNKS, config.searchRadiusChunks));
        if (!java.util.Set.of("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
                .contains(config.hudAnchor)) {
            config.hudAnchor = "BOTTOM_RIGHT";
        }
        config.hudOffsetX = Math.max(0, Math.min(200, config.hudOffsetX));
        config.hudOffsetY = Math.max(0, Math.min(200, config.hudOffsetY));
        return config;
    }

    private static void ensureExists() {
        if (!Files.exists(PATH)) {
            save(defaults());
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

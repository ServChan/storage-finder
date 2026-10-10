package org.lts.storagefinder.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutoPeekConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("storage_finder_macro.json");
    private static AutoPeekConfig instance = new AutoPeekConfig();

    public boolean enabled = true;
    public double reach = 4.0;
    public int rescanSeconds = 300;
    public int failedRetrySeconds = 600;
    public int holdTicks = 6;
    public int cooldownTicks = 6;
    public int menuTimeoutTicks = 20;
    public boolean skipTrappedChests = true;
    public boolean muteSounds = true;
    public boolean pauseWhileUsingKeys = true;

    public static AutoPeekConfig current() {
        return instance;
    }

    public static void load() {
        if (Files.isRegularFile(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                AutoPeekConfig loaded = GSON.fromJson(reader, AutoPeekConfig.class);
                if (loaded != null) {
                    instance = sanitize(loaded);
                }
            } catch (Exception exception) {
                AutoPeekClient.LOGGER.warn("Could not read {}; using defaults", PATH, exception);
            }
        }
        save();
    }

    public static void save() {
        AutoPeekConfig snapshot = instance;
        Thread.ofVirtual().name("storagefinder-macro-config").start(() -> {
            try {
                Files.createDirectories(PATH.getParent());
                try (Writer writer = Files.newBufferedWriter(PATH)) {
                    GSON.toJson(snapshot, writer);
                }
            } catch (Exception exception) {
                AutoPeekClient.LOGGER.warn("Could not save {}", PATH, exception);
            }
        });
    }

    private static AutoPeekConfig sanitize(AutoPeekConfig config) {
        config.reach = Math.clamp(config.reach, 1.0, 6.0);
        config.rescanSeconds = Math.clamp(config.rescanSeconds, 10, 86_400);
        config.failedRetrySeconds = Math.clamp(config.failedRetrySeconds, 10, 86_400);
        config.holdTicks = Math.clamp(config.holdTicks, 1, 40);
        config.cooldownTicks = Math.clamp(config.cooldownTicks, 0, 100);
        config.menuTimeoutTicks = Math.clamp(config.menuTimeoutTicks, 5, 100);
        return config;
    }
}

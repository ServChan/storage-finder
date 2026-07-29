package org.lts.storagefinder;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class EconomyPriceBridge {
    private static final String ECONOMY_MOD_ID = "ltsservereconomy";
    private static final String API_KEY = "ltsservereconomy:unit-price-summary-v1";
    private static final int API_VERSION = 1;
    private static final long CACHE_MILLIS = 1_000L;
    private static final Map<String, CachedSummary> CACHE = new ConcurrentHashMap<>();

    private static volatile Function<Collection<String>, ?> provider;
    private static volatile boolean failureLogged;

    private EconomyPriceBridge() {
    }

    public static void initialize() {
        ObjectShare objectShare = FabricLoader.getInstance().getObjectShare();
        registerProvider(objectShare.get(API_KEY));
        objectShare.whenAvailable(API_KEY, (key, value) -> registerProvider(value));
    }

    public static boolean isEconomyInstalled() {
        return FabricLoader.getInstance().isModLoaded(ECONOMY_MOD_ID);
    }

    public static boolean isAvailable() {
        return provider != null;
    }

    public static Optional<PriceSummary> lookup(Collection<String> itemIds) {
        Function<Collection<String>, ?> currentProvider = provider;
        if (currentProvider == null || itemIds == null || itemIds.isEmpty()) {
            return Optional.empty();
        }
        List<String> normalizedIds = itemIds.stream()
                .filter(itemId -> itemId != null && !itemId.isBlank())
                .map(String::strip)
                .distinct()
                .sorted()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Optional.empty();
        }

        String cacheKey = String.join("\u0000", normalizedIds);
        long now = System.currentTimeMillis();
        CachedSummary cached = CACHE.get(cacheKey);
        if (cached != null && now - cached.createdAt() < CACHE_MILLIS) {
            return cached.summary();
        }

        Optional<PriceSummary> summary;
        try {
            summary = parse(currentProvider.apply(normalizedIds));
        } catch (RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                StorageFinderClient.LOGGER.warn("Economy price bridge lookup failed", exception);
            }
            summary = Optional.empty();
        }
        CACHE.put(cacheKey, new CachedSummary(now, summary));
        return summary;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @SuppressWarnings("unchecked")
    private static void registerProvider(Object candidate) {
        if (candidate == null) {
            return;
        }
        if (!(candidate instanceof Function<?, ?> function)) {
            StorageFinderClient.LOGGER.warn("Economy bridge {} has unsupported provider type {}",
                    API_KEY, candidate.getClass().getName());
            return;
        }
        Function<Collection<String>, ?> registered =
                (Function<Collection<String>, ?>) (Function<?, ?>) function;
        if (provider == registered) {
            return;
        }
        provider = registered;
        failureLogged = false;
        CACHE.clear();
        StorageFinderClient.LOGGER.info("Connected to Economy price bridge {}", API_KEY);
    }

    private static Optional<PriceSummary> parse(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Optional.empty();
        }
        Number version = number(map.get("apiVersion"));
        Number minimum = number(map.get("minimumUnitPrice"));
        Number average = number(map.get("averageUnitPrice"));
        Number offers = number(map.get("offers"));
        Number freshestAt = number(map.get("freshestAt"));
        if (version == null || version.intValue() != API_VERSION
                || minimum == null || average == null || offers == null || freshestAt == null
                || !Double.isFinite(minimum.doubleValue())
                || !Double.isFinite(average.doubleValue())
                || minimum.doubleValue() < 0.0 || average.doubleValue() < minimum.doubleValue()
                || offers.intValue() < 1) {
            return Optional.empty();
        }
        String currency = map.get("currency") instanceof String text && !text.isBlank()
                ? text.strip() : "AP";
        return Optional.of(new PriceSummary(minimum.doubleValue(), average.doubleValue(),
                offers.intValue(), freshestAt.longValue(), currency));
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    public record PriceSummary(double minimumUnitPrice, double averageUnitPrice,
                               int offers, long freshestAt, String currency) {
    }

    private record CachedSummary(long createdAt, Optional<PriceSummary> summary) {
    }
}

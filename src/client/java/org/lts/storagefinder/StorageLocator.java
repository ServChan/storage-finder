package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StorageLocator {
    private static final int MAX_ROUTES_PER_COLOR = 3;
    private static final int FAILED_ROUTE_RETRY_REFRESHES = 10;
    private final Map<BlockPos, List<Integer>> matches = new LinkedHashMap<>();
    private final Map<Integer, List<Route>> routesByColor = new LinkedHashMap<>();
    private final Map<Integer, String> routeDiagnostics = new HashMap<>();
    private final Map<Integer, FailedRouteAttempt> failedRouteAttempts = new HashMap<>();
    private long refreshSerial;

    public void clear() {
        matches.clear();
        routesByColor.clear();
        routeDiagnostics.clear();
        failedRouteAttempts.clear();
    }

    public void refresh(Minecraft minecraft, SearchSelection selection, StorageIndex index) {
        matches.clear();
        refreshSerial++;
        if (minecraft.level == null || minecraft.player == null || selection.isEmpty()) {
            routesByColor.clear();
            routeDiagnostics.clear();
            failedRouteAttempts.clear();
            return;
        }

        StorageFinderConfig config = StorageFinderConfig.current();

        Map<BlockPos, Set<String>> itemsByStorage = new HashMap<>();
        Map<Integer, List<BlockPos>> candidatesByColor = new LinkedHashMap<>();
        selection.entries().values().forEach(color -> candidatesByColor.put(color, new ArrayList<>()));
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double radius = config.radiusBlocks();
        AABB area = new AABB(playerX - radius, minecraft.level.getMinY(), playerZ - radius,
                playerX + radius, minecraft.level.getMaxY(), playerZ + radius);
        if (config.searchItemFrames) for (ItemFrame frame : minecraft.level.getEntitiesOfClass(ItemFrame.class, area)) {
            if (frame.getItem().isEmpty()) {
                continue;
            }
            BlockPos support = frame.getPos().relative(frame.getDirection().getOpposite());
            if (!withinSearchRadius(playerX, playerZ, support) || !isStorage(minecraft, support)) {
                continue;
            }
            BlockPos canonical = StorageIndex.canonicalPos(minecraft, support);
            itemsByStorage.computeIfAbsent(canonical, ignored -> new LinkedHashSet<>())
                    .addAll(SearchSelection.itemIds(frame.getItem()));
        }

        String scope = ScopeUtil.current(minecraft);
        if (config.searchRememberedContents) {
            index.migrateLegacyScopeIfNearby(scope, minecraft.player.blockPosition());
        }
        if (config.searchRememberedContents) for (StorageIndex.Record record : index.recordsInScope(scope)) {
            BlockPos pos = record.pos();
            if (!minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            if (isStorage(minecraft, pos) && storageWithinSearchRadius(minecraft, playerX, playerZ, pos)) {
                itemsByStorage.computeIfAbsent(pos, ignored -> new LinkedHashSet<>()).addAll(record.items);
            }
        }

        for (Map.Entry<BlockPos, Set<String>> storage : itemsByStorage.entrySet()) {
            Set<Integer> colors = new LinkedHashSet<>();
            for (Map.Entry<String, Integer> selected : selection.entries().entrySet()) {
                if (storage.getValue().contains(selected.getKey())) {
                    colors.add(selected.getValue());
                    List<BlockPos> candidates = candidatesByColor.computeIfAbsent(
                            selected.getValue(), ignored -> new ArrayList<>());
                    if (!candidates.contains(storage.getKey())) {
                        candidates.add(storage.getKey());
                    }
                }
            }
            if (!colors.isEmpty()) {
                for (BlockPos physical : StorageIndex.physicalBlocks(minecraft, storage.getKey())) {
                    if (withinSearchRadius(playerX, playerZ, physical)) {
                        matches.put(physical, List.copyOf(colors));
                    }
                }
            }
        }

        updateRoutes(minecraft, candidatesByColor);
    }

    public Map<BlockPos, List<Integer>> matches() {
        return matches;
    }

    public Map<Integer, List<Route>> routesByColor() {
        return routesByColor;
    }

    public static boolean isStorage(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }
        var block = minecraft.level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock;
    }

    public static boolean withinSearchRadius(BlockPos center, BlockPos pos) {
        return withinSearchRadius(center.getX() + 0.5, center.getZ() + 0.5, pos);
    }

    public static boolean withinSearchRadius(double centerX, double centerZ, BlockPos pos) {
        double dx = pos.getX() + 0.5 - centerX;
        double dz = pos.getZ() + 0.5 - centerZ;
        double radius = StorageFinderConfig.current().radiusBlocks();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static boolean storageWithinSearchRadius(Minecraft minecraft, double centerX, double centerZ, BlockPos canonical) {
        for (BlockPos physical : StorageIndex.physicalBlocks(minecraft, canonical)) {
            if (withinSearchRadius(centerX, centerZ, physical)) {
                return true;
            }
        }
        return false;
    }

    private void updateRoutes(Minecraft minecraft, Map<Integer, List<BlockPos>> candidatesByColor) {
        if (!StorageFinderConfig.current().routeEnabled) {
            routesByColor.clear();
            routeDiagnostics.clear();
            failedRouteAttempts.clear();
            return;
        }
        routesByColor.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeDiagnostics.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        failedRouteAttempts.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        BlockPos start = minecraft.player.blockPosition();
        for (Map.Entry<Integer, List<BlockPos>> entry : candidatesByColor.entrySet()) {
            List<BlockPos> candidates = entry.getValue();
            candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            FailedRouteAttempt failedAttempt = failedRouteAttempts.get(entry.getKey());
            if (failedAttempt != null
                    && refreshSerial - failedAttempt.createdAtRefresh < FAILED_ROUTE_RETRY_REFRESHES
                    && failedAttempt.start.equals(start)
                    && failedAttempt.candidates.equals(candidates)) {
                routesByColor.remove(entry.getKey());
                continue;
            }
            List<Route> cached = routesByColor.get(entry.getKey());
            if (cached != null && !cached.isEmpty() && refreshSerial - cached.getFirst().createdAtRefresh < 10) {
                List<Route> rebased = rebaseCachedRoutes(minecraft, cached, candidates, start);
                if (rebased.size() == cached.size()) {
                    routesByColor.put(entry.getKey(), rebased);
                    continue;
                }
            }

            List<Route> found = new ArrayList<>();
            List<BlockPos> remaining = new ArrayList<>(candidates);
            for (int routeIndex = 0; routeIndex < MAX_ROUTES_PER_COLOR && !remaining.isEmpty(); routeIndex++) {
                RouteFinder.Result result = RouteFinder.findAny(minecraft, start, remaining);
                if (result == null) {
                    break;
                }
                found.add(new Route(start.immutable(), result.target().immutable(), result.path(), refreshSerial));
                remaining.removeIf(pos -> pos.equals(result.target()));
            }
            if (!found.isEmpty()) {
                routesByColor.put(entry.getKey(), List.copyOf(found));
                failedRouteAttempts.remove(entry.getKey());
            } else {
                routesByColor.remove(entry.getKey());
                if (!candidates.isEmpty()) {
                    failedRouteAttempts.put(entry.getKey(), new FailedRouteAttempt(
                            start.immutable(), List.copyOf(candidates), refreshSerial));
                } else {
                    failedRouteAttempts.remove(entry.getKey());
                }
            }
            logRouteState(entry.getKey(), candidates.size(), found.size());
        }
    }

    private void logRouteState(int color, int candidates, int routes) {
        String state = candidates + ":" + routes;
        if (state.equals(routeDiagnostics.put(color, state))) {
            return;
        }
        if (candidates > 0 && routes == 0) {
            StorageFinderClient.LOGGER.warn("No reachable route for color #{}, candidates={}",
                    String.format("%06X", color & 0xFFFFFF), candidates);
        } else {
            StorageFinderClient.LOGGER.info("Route state for color #{}: candidates={}, routes={}",
                    String.format("%06X", color & 0xFFFFFF), candidates, routes);
        }
    }

    private List<Route> rebaseCachedRoutes(Minecraft minecraft, List<Route> cached,
                                            List<BlockPos> candidates, BlockPos start) {
        List<Route> rebased = new ArrayList<>(cached.size());
        for (Route route : cached) {
            if (!candidates.contains(route.target)) {
                return List.of();
            }
            int startIndex = route.path.indexOf(start);
            if (startIndex < 0) {
                return List.of();
            }
            List<BlockPos> remainingPath = List.copyOf(route.path.subList(startIndex, route.path.size()));
            if (!RouteFinder.isRouteValid(minecraft, route.target, remainingPath)) {
                return List.of();
            }
            rebased.add(new Route(start.immutable(), route.target, remainingPath, route.createdAtRefresh));
        }
        return List.copyOf(rebased);
    }

    public record Route(BlockPos start, BlockPos target, List<BlockPos> path, long createdAtRefresh) {
    }

    private record FailedRouteAttempt(BlockPos start, List<BlockPos> candidates, long createdAtRefresh) {
    }
}

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
    private static final int ROUTE_NODE_BUDGET_PER_TICK = 800;
    private final Map<BlockPos, List<Integer>> matches = new LinkedHashMap<>();
    private final Map<Integer, List<Route>> routesByColor = new LinkedHashMap<>();
    private final Map<Integer, String> routeDiagnostics = new HashMap<>();
    private final Map<Integer, FailedRouteAttempt> failedRouteAttempts = new HashMap<>();
    private final Map<Integer, List<BlockPos>> latestCandidatesByColor = new LinkedHashMap<>();
    private final Map<Integer, RouteJob> routeJobs = new LinkedHashMap<>();
    private long refreshSerial;

    public void clear() {
        matches.clear();
        routesByColor.clear();
        routeDiagnostics.clear();
        failedRouteAttempts.clear();
        latestCandidatesByColor.clear();
        routeJobs.clear();
    }

    public void refresh(Minecraft minecraft, SearchSelection selection, StorageIndex index) {
        matches.clear();
        refreshSerial++;
        if (minecraft.level == null || minecraft.player == null || selection.isEmpty()) {
            routesByColor.clear();
            routeDiagnostics.clear();
            failedRouteAttempts.clear();
            latestCandidatesByColor.clear();
            routeJobs.clear();
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

    public Map<Integer, QueryStats> queryStats() {
        Map<Integer, QueryStats> stats = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<BlockPos>> entry : latestCandidatesByColor.entrySet()) {
            stats.put(entry.getKey(), new QueryStats(entry.getValue().size(),
                    routesByColor.getOrDefault(entry.getKey(), List.of()).size(),
                    routeJobs.containsKey(entry.getKey())));
        }
        return Map.copyOf(stats);
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
        latestCandidatesByColor.clear();
        candidatesByColor.forEach((color, candidates) ->
                latestCandidatesByColor.put(color, List.copyOf(candidates)));
        if (!StorageFinderConfig.current().routeEnabled) {
            routesByColor.clear();
            routeDiagnostics.clear();
            failedRouteAttempts.clear();
            routeJobs.clear();
            return;
        }
        routesByColor.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeDiagnostics.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        failedRouteAttempts.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeJobs.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        BlockPos start = minecraft.player.blockPosition();
        for (Map.Entry<Integer, List<BlockPos>> entry : candidatesByColor.entrySet()) {
            List<BlockPos> candidates = entry.getValue();
            candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            latestCandidatesByColor.put(entry.getKey(), List.copyOf(candidates));

            RouteJob existingJob = routeJobs.get(entry.getKey());
            if (existingJob != null && existingJob.matches(start, candidates)) {
                continue;
            }
            routeJobs.remove(entry.getKey());

            List<Route> cached = routesByColor.get(entry.getKey());
            if (cached != null && !cached.isEmpty()) {
                List<Route> rebased = rebaseCachedRoutes(minecraft, cached, candidates, start);
                if (rebased.size() == cached.size()) {
                    routesByColor.put(entry.getKey(), rebased);
                    if (refreshSerial - cached.getFirst().createdAtRefresh < 10) {
                        continue;
                    }
                } else {
                    routesByColor.remove(entry.getKey());
                }
            }

            FailedRouteAttempt failedAttempt = failedRouteAttempts.get(entry.getKey());
            if (failedAttempt != null
                    && refreshSerial - failedAttempt.createdAtRefresh < FAILED_ROUTE_RETRY_REFRESHES
                    && failedAttempt.start.equals(start)
                    && failedAttempt.candidates.equals(candidates)) {
                routesByColor.remove(entry.getKey());
                continue;
            }
            if (candidates.isEmpty()) {
                routesByColor.remove(entry.getKey());
                failedRouteAttempts.remove(entry.getKey());
                logRouteState(entry.getKey(), 0, 0);
                continue;
            }
            routeJobs.put(entry.getKey(), new RouteJob(start, candidates));
        }
    }

    public void tickRoutes(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null
                || !StorageFinderConfig.current().routeEnabled) {
            routeJobs.clear();
            return;
        }
        if (routeJobs.isEmpty()) {
            return;
        }

        int budgetPerJob = Math.max(64, ROUTE_NODE_BUDGET_PER_TICK / routeJobs.size());
        for (Integer color : new ArrayList<>(routeJobs.keySet())) {
            RouteJob job = routeJobs.get(color);
            if (job == null) {
                continue;
            }
            if (job.search == null) {
                if (job.found.size() >= MAX_ROUTES_PER_COLOR || job.remaining.isEmpty()) {
                    finishRouteJob(color, job);
                    continue;
                }
                job.search = RouteFinder.begin(minecraft, job.start, job.remaining);
                if (job.search == null) {
                    finishRouteJob(color, job);
                    continue;
                }
            }

            RouteFinder.StepStatus status = job.search.step(minecraft, budgetPerJob);
            if (status == RouteFinder.StepStatus.FOUND) {
                RouteFinder.Result result = job.search.result();
                job.found.add(new Route(job.start, result.target().immutable(),
                        result.path(), refreshSerial));
                job.remaining.removeIf(pos -> pos.equals(result.target()));
                job.search = null;
                if (job.found.size() >= MAX_ROUTES_PER_COLOR || job.remaining.isEmpty()) {
                    finishRouteJob(color, job);
                }
            } else if (status == RouteFinder.StepStatus.FAILED) {
                finishRouteJob(color, job);
            }
        }
    }

    private void finishRouteJob(int color, RouteJob job) {
        routeJobs.remove(color);
        if (job.found.isEmpty()) {
            routesByColor.remove(color);
            failedRouteAttempts.put(color, new FailedRouteAttempt(
                    job.start, job.candidates, refreshSerial));
        } else {
            routesByColor.put(color, List.copyOf(job.found));
            failedRouteAttempts.remove(color);
        }
        logRouteState(color, job.candidates.size(), job.found.size());
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

    public record QueryStats(int matches, int routes, boolean searching) {
    }

    private static final class RouteJob {
        private final BlockPos start;
        private final List<BlockPos> candidates;
        private final List<BlockPos> remaining;
        private final List<Route> found = new ArrayList<>();
        private RouteFinder.Search search;

        private RouteJob(BlockPos start, List<BlockPos> candidates) {
            this.start = start.immutable();
            this.candidates = List.copyOf(candidates);
            this.remaining = new ArrayList<>(candidates);
        }

        private boolean matches(BlockPos currentStart, List<BlockPos> currentCandidates) {
            return start.equals(currentStart) && candidates.equals(currentCandidates);
        }
    }

    private record FailedRouteAttempt(BlockPos start, List<BlockPos> candidates, long createdAtRefresh) {
    }
}

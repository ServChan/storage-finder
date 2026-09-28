package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    private static final int FAILED_ROUTE_RETRY_REFRESHES = 10;
    private static final int ROUTE_REFRESH_INTERVAL = 10;
    private static final int START_TOLERANCE_HORIZONTAL = 4;
    private static final int START_TOLERANCE_VERTICAL = 2;
    private static final int MIN_ROUTE_CANDIDATES = 12;
    private static final int ROUTE_CANDIDATES_PER_TARGET = 6;
    private final Map<BlockPos, List<Integer>> matches = new LinkedHashMap<>();
    private final Map<Integer, List<Route>> routesByColor = new LinkedHashMap<>();
    private final Map<Integer, String> routeDiagnostics = new HashMap<>();
    private final Map<Integer, FailedRouteAttempt> failedRouteAttempts = new HashMap<>();
    private final Map<Integer, List<BlockPos>> latestCandidatesByColor = new LinkedHashMap<>();
    private final Map<Integer, Integer> latestItemCountsByColor = new LinkedHashMap<>();
    private final Map<BlockPos, IndexFreshness> latestFreshnessByStorage = new HashMap<>();
    private final Map<Integer, Map<BlockPos, IndexFreshness>> latestFreshnessByColor = new HashMap<>();
    private final Map<Integer, Double> latestNearestDistanceByColor = new LinkedHashMap<>();
    private final Map<Integer, RouteFinder.FailureReason> routeFailures = new HashMap<>();
    private final Map<Integer, RouteJob> routeJobs = new LinkedHashMap<>();
    private long refreshSerial;
    private int routeJobCursor;

    public void clear() {
        matches.clear();
        routesByColor.clear();
        routeDiagnostics.clear();
        failedRouteAttempts.clear();
        latestCandidatesByColor.clear();
        latestItemCountsByColor.clear();
        latestFreshnessByStorage.clear();
        latestFreshnessByColor.clear();
        latestNearestDistanceByColor.clear();
        routeFailures.clear();
        routeJobs.clear();
    }

    public void refresh(Minecraft minecraft, SearchSelection selection, StorageIndex index) {
        matches.clear();
        latestFreshnessByStorage.clear();
        latestFreshnessByColor.clear();
        refreshSerial++;
        if (minecraft.level == null || minecraft.player == null || selection.isEmpty()) {
            routesByColor.clear();
            routeDiagnostics.clear();
            failedRouteAttempts.clear();
            latestCandidatesByColor.clear();
            latestItemCountsByColor.clear();
            latestNearestDistanceByColor.clear();
            routeFailures.clear();
            routeJobs.clear();
            return;
        }

        StorageFinderConfig config = StorageFinderConfig.current();

        Map<BlockPos, Map<String, Integer>> itemsByStorage = new HashMap<>();
        Map<BlockPos, Set<String>> rememberedItemsByStorage = new HashMap<>();
        Map<Integer, LinkedHashSet<BlockPos>> candidateSetsByColor = new LinkedHashMap<>();
        selection.entries().values().forEach(color ->
                candidateSetsByColor.computeIfAbsent(color, ignored -> new LinkedHashSet<>()));
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        long now = System.currentTimeMillis();
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
            Map<String, Integer> frameItems = itemsByStorage.computeIfAbsent(canonical,
                    ignored -> new LinkedHashMap<>());
            SearchSelection.itemIds(frame.getItem()).forEach(item -> frameItems.putIfAbsent(item, 0));
            latestFreshnessByStorage.putIfAbsent(canonical, IndexFreshness.FRAME_ONLY);
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
                Map<String, Integer> remembered = itemsByStorage.computeIfAbsent(pos,
                        ignored -> new LinkedHashMap<>());
                record.items.forEach(item -> remembered.putIfAbsent(item, 0));
                rememberedItemsByStorage.computeIfAbsent(pos, ignored -> new LinkedHashSet<>())
                        .addAll(record.items);
                record.normalizedCounts().forEach((item, count) -> remembered.merge(item, count, Math::max));
                latestFreshnessByStorage.put(pos,
                        IndexFreshness.classify(record.updatedAt, now));
            }
        }

        Map<Integer, Integer> itemCountsByColor = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Map<String, Integer>> storage : itemsByStorage.entrySet()) {
            Set<Integer> colors = new LinkedHashSet<>();
            Map<Integer, Integer> countsInStorage = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> selected : selection.entries().entrySet()) {
                if (storage.getValue().containsKey(selected.getKey())) {
                    colors.add(selected.getValue());
                    countsInStorage.merge(selected.getValue(), storage.getValue().get(selected.getKey()), Math::max);
                    candidateSetsByColor.computeIfAbsent(selected.getValue(), ignored -> new LinkedHashSet<>())
                            .add(storage.getKey());
                    IndexFreshness sourceFreshness = rememberedItemsByStorage
                            .getOrDefault(storage.getKey(), Set.of()).contains(selected.getKey())
                            ? latestFreshnessByStorage.getOrDefault(storage.getKey(), IndexFreshness.STALE)
                            : IndexFreshness.FRAME_ONLY;
                    latestFreshnessByColor.computeIfAbsent(selected.getValue(), ignored -> new HashMap<>())
                            .merge(storage.getKey(), sourceFreshness, StorageLocator::preferFresher);
                }
            }
            countsInStorage.forEach((color, count) -> itemCountsByColor.merge(color, count, Integer::sum));
            if (!colors.isEmpty()) {
                for (BlockPos physical : StorageIndex.physicalBlocks(minecraft, storage.getKey())) {
                    if (withinSearchRadius(playerX, playerZ, physical)) {
                        matches.put(physical, List.copyOf(colors));
                    }
                }
            }
        }

        latestItemCountsByColor.clear();
        latestItemCountsByColor.putAll(itemCountsByColor);
        Map<Integer, List<BlockPos>> candidatesByColor = new LinkedHashMap<>();
        candidateSetsByColor.forEach((color, candidates) ->
                candidatesByColor.put(color, new ArrayList<>(candidates)));
        latestNearestDistanceByColor.clear();
        candidatesByColor.forEach((color, candidates) -> candidates.stream()
                .mapToDouble(pos -> Math.sqrt(pos.distToCenterSqr(
                        minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())))
                .min().ifPresent(distance -> latestNearestDistanceByColor.put(color, distance)));
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
            int fresh = 0;
            int aging = 0;
            int stale = 0;
            int frames = 0;
            for (BlockPos pos : entry.getValue()) {
                switch (latestFreshnessByColor.getOrDefault(entry.getKey(), Map.of())
                        .getOrDefault(pos, IndexFreshness.FRAME_ONLY)) {
                    case FRESH -> fresh++;
                    case AGING -> aging++;
                    case STALE -> stale++;
                    case FRAME_ONLY -> frames++;
                }
            }
            List<Route> routes = routesByColor.getOrDefault(entry.getKey(), List.of());
            double routeDistance = routes.stream().mapToDouble(Route::length).min().orElse(Double.NaN);
            stats.put(entry.getKey(), new QueryStats(entry.getValue().size(), routes.size(),
                    routeJobs.containsKey(entry.getKey()), latestItemCountsByColor.getOrDefault(entry.getKey(), 0),
                    fresh, aging, stale, frames,
                    latestNearestDistanceByColor.getOrDefault(entry.getKey(), Double.NaN),
                    routeDistance, routeFailures.get(entry.getKey())));
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

    private static IndexFreshness preferFresher(IndexFreshness first, IndexFreshness second) {
        return freshnessRank(first) <= freshnessRank(second) ? first : second;
    }

    private static int freshnessRank(IndexFreshness freshness) {
        return switch (freshness) {
            case FRESH -> 0;
            case AGING -> 1;
            case STALE -> 2;
            case FRAME_ONLY -> 3;
        };
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
            routeFailures.clear();
            return;
        }
        routesByColor.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeDiagnostics.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        failedRouteAttempts.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeJobs.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        routeFailures.keySet().removeIf(color -> !candidatesByColor.containsKey(color));
        BlockPos start = RouteFinder.resolveStart(minecraft, minecraft.player.blockPosition());
        if (start == null) {
            candidatesByColor.keySet().forEach(color -> {
                if (!routesByColor.containsKey(color) && !routeJobs.containsKey(color)) {
                    routeFailures.put(color, RouteFinder.FailureReason.NO_START);
                }
            });
            return;
        }
        for (Map.Entry<Integer, List<BlockPos>> entry : candidatesByColor.entrySet()) {
            int color = entry.getKey();
            List<BlockPos> candidates = entry.getValue();
            candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            latestCandidatesByColor.put(color, List.copyOf(candidates));
            Set<BlockPos> candidateSet = Set.copyOf(candidates);

            List<Route> cached = routesByColor.get(color);
            List<Route> rebased = List.of();
            if (cached != null && !cached.isEmpty()) {
                rebased = rebaseCachedRoutes(minecraft, cached, candidateSet, start);
                if (rebased.isEmpty()) {
                    routesByColor.remove(color);
                } else {
                    routesByColor.put(color, rebased);
                }
            }

            RouteJob existingJob = routeJobs.get(color);
            if (existingJob != null && existingJob.canContinue(start, candidateSet)) {
                continue;
            }
            routeJobs.remove(color);
            if (!rebased.isEmpty() && rebased.size() == cached.size()
                    && refreshSerial - rebased.getFirst().createdAtRefresh < ROUTE_REFRESH_INTERVAL) {
                continue;
            }

            FailedRouteAttempt failedAttempt = failedRouteAttempts.get(color);
            if (failedAttempt != null
                    && refreshSerial - failedAttempt.createdAtRefresh < FAILED_ROUTE_RETRY_REFRESHES
                    && isNearStart(failedAttempt.start, start)
                    && failedAttempt.candidates.equals(candidateSet)) {
                continue;
            }
            if (candidates.isEmpty()) {
                routesByColor.remove(color);
                failedRouteAttempts.remove(color);
                routeFailures.remove(color);
                logRouteState(color, 0, 0);
                continue;
            }
            routeFailures.remove(color);
            routeJobs.put(color, new RouteJob(start, candidates, candidateSet, !rebased.isEmpty()));
        }
    }

    public void tickRoutes(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null
                || !StorageFinderConfig.current().routeEnabled) {
            routeJobs.clear();
            return;
        }
        BlockPos currentStart = RouteFinder.resolveStart(minecraft, minecraft.player.blockPosition());
        if (currentStart == null) {
            return;
        }
        if (hasOutdatedRouteStart(currentStart)) {
            Map<Integer, List<BlockPos>> candidates = new LinkedHashMap<>();
            latestCandidatesByColor.forEach((color, positions) ->
                    candidates.put(color, new ArrayList<>(positions)));
            updateRoutes(minecraft, candidates);
        }
        if (routeJobs.isEmpty()) {
            return;
        }

        StorageFinderConfig config = StorageFinderConfig.current();
        List<Integer> colors = new ArrayList<>(routeJobs.keySet());
        int totalBudget = config.routeNodeBudget;
        int jobsToProcess = Math.min(colors.size(), totalBudget);
        int baseBudget = totalBudget / jobsToProcess;
        int extraBudget = totalBudget % jobsToProcess;
        int startIndex = Math.floorMod(routeJobCursor, colors.size());
        for (int i = 0; i < jobsToProcess; i++) {
            Integer color = colors.get((startIndex + i) % colors.size());
            RouteJob job = routeJobs.get(color);
            if (job == null) {
                continue;
            }
            if (job.search == null) {
                job.start = currentStart.immutable();
                int limit = Math.max(MIN_ROUTE_CANDIDATES,
                        config.routeMaxRoutes * ROUTE_CANDIDATES_PER_TARGET);
                List<BlockPos> searchCandidates = job.candidates.subList(0,
                        Math.min(job.candidates.size(), limit));
                job.search = RouteFinder.begin(minecraft, job.start, searchCandidates, config.routeMaxRoutes);
                if (job.search == null) {
                    finishRouteJob(color, job);
                    continue;
                }
            }

            int remaining = baseBudget + (i < extraBudget ? 1 : 0);
            while (remaining > 0) {
                RouteFinder.StepStatus status = job.search.step(minecraft, remaining);
                remaining -= Math.max(1, job.search.lastProcessed());
                if (status == RouteFinder.StepStatus.FOUND) {
                    RouteFinder.Result result = job.search.result();
                    job.found.add(createRoute(minecraft, result.target(), result.path()));
                    publishProgress(color, job);
                    if (job.search.completeAfterResult()) {
                        finishRouteJob(color, job);
                        break;
                    }
                } else if (status == RouteFinder.StepStatus.FAILED
                        || status == RouteFinder.StepStatus.COMPLETE) {
                    finishRouteJob(color, job);
                    break;
                } else {
                    break;
                }
            }
        }
        routeJobCursor = (startIndex + jobsToProcess) % colors.size();
    }

    private boolean hasOutdatedRouteStart(BlockPos currentStart) {
        for (RouteJob job : routeJobs.values()) {
            if (!isNearStart(job.start, currentStart)) {
                return true;
            }
        }
        for (List<Route> routes : routesByColor.values()) {
            if (!routes.isEmpty() && !routes.getFirst().start.equals(currentStart)) {
                return true;
            }
        }
        for (FailedRouteAttempt attempt : failedRouteAttempts.values()) {
            if (!isNearStart(attempt.start, currentStart)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearStart(BlockPos first, BlockPos second) {
        return RouteMath.isNear(first.getX(), first.getY(), first.getZ(),
                second.getX(), second.getY(), second.getZ(),
                START_TOLERANCE_HORIZONTAL, START_TOLERANCE_VERTICAL);
    }

    private void publishProgress(int color, RouteJob job) {
        List<Route> displayed = routesByColor.get(color);
        if (!job.replacing || displayed == null || job.found.size() >= displayed.size()) {
            routesByColor.put(color, List.copyOf(job.found));
        }
    }

    private void finishRouteJob(int color, RouteJob job) {
        routeJobs.remove(color);
        List<Route> displayed = routesByColor.getOrDefault(color, List.of());
        if (job.found.isEmpty() && job.replacing && !displayed.isEmpty()) {
            routesByColor.put(color, displayed.stream()
                    .map(route -> route.refreshedAt(refreshSerial))
                    .toList());
            failedRouteAttempts.remove(color);
            routeFailures.remove(color);
        } else if (job.found.isEmpty()) {
            routesByColor.remove(color);
            failedRouteAttempts.put(color, new FailedRouteAttempt(
                    job.start, job.candidateSet, refreshSerial));
            routeFailures.put(color, job.search == null || job.search.failureReason() == null
                    ? RouteFinder.FailureReason.NO_REACHABLE_GOAL
                    : job.search.failureReason());
        } else {
            routesByColor.put(color, List.copyOf(job.found));
            failedRouteAttempts.remove(color);
            routeFailures.remove(color);
        }
        logRouteState(color, job.candidates.size(), routesByColor.getOrDefault(color, List.of()).size());
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
            StorageFinderClient.LOGGER.debug("Route state for color #{}: candidates={}, routes={}",
                    String.format("%06X", color & 0xFFFFFF), candidates, routes);
        }
    }

    private List<Route> rebaseCachedRoutes(Minecraft minecraft, List<Route> cached,
                                            Set<BlockPos> candidates, BlockPos start) {
        List<Route> rebased = new ArrayList<>(cached.size());
        for (Route route : cached) {
            if (!candidates.contains(route.target)) {
                continue;
            }
            List<BlockPos> remainingPath = trimToStart(minecraft, route.path, start);
            if (remainingPath == null || !RouteFinder.isRouteValid(minecraft, route.target, remainingPath)) {
                continue;
            }
            List<Vec3> renderPoints = remainingPath.equals(route.path)
                    ? route.renderPoints : buildRenderPoints(minecraft, remainingPath);
            rebased.add(new Route(start.immutable(), route.target, remainingPath,
                    renderPoints, route.createdAtRefresh, RouteFinder.pathLength(remainingPath)));
        }
        return List.copyOf(rebased);
    }

    private static List<BlockPos> trimToStart(Minecraft minecraft, List<BlockPos> path, BlockPos start) {
        int exact = path.lastIndexOf(start);
        if (exact >= 0) {
            return exact == 0 ? path : List.copyOf(path.subList(exact, path.size()));
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            BlockPos node = path.get(i);
            if (RouteMath.isNear(node.getX(), node.getY(), node.getZ(),
                    start.getX(), start.getY(), start.getZ(), 1, 1)
                    && RouteFinder.isPathValid(minecraft, List.of(start, node))) {
                List<BlockPos> snapped = new ArrayList<>(path.size() - i + 1);
                snapped.add(start.immutable());
                snapped.addAll(path.subList(i, path.size()));
                return List.copyOf(snapped);
            }
        }
        return null;
    }

    private Route createRoute(Minecraft minecraft, BlockPos target, List<BlockPos> path) {
        List<BlockPos> immutablePath = List.copyOf(path);
        return new Route(immutablePath.getFirst(), target.immutable(), immutablePath,
                buildRenderPoints(minecraft, immutablePath), refreshSerial,
                RouteFinder.pathLength(immutablePath));
    }

    private static List<Vec3> buildRenderPoints(Minecraft minecraft, List<BlockPos> path) {
        if (path.isEmpty()) {
            return List.of();
        }
        List<Vec3> raw = new ArrayList<>(path.size());
        for (BlockPos pos : path) {
            double surface = RouteFinder.surfaceY(minecraft, pos);
            if (!Double.isFinite(surface)) {
                surface = pos.getY();
            }
            raw.add(new Vec3(pos.getX() + 0.5, surface + 0.10, pos.getZ() + 0.5));
        }
        if (raw.size() < 3) {
            return List.copyOf(raw);
        }

        List<Vec3> simplified = new ArrayList<>();
        simplified.add(raw.getFirst());
        for (int i = 1; i < raw.size() - 1; i++) {
            Vec3 previous = raw.get(i - 1);
            Vec3 current = raw.get(i);
            Vec3 next = raw.get(i + 1);
            double firstX = current.x - previous.x;
            double firstY = current.y - previous.y;
            double firstZ = current.z - previous.z;
            double secondX = next.x - current.x;
            double secondY = next.y - current.y;
            double secondZ = next.z - current.z;
            if (Math.abs(firstX - secondX) > 1.0E-7
                    || Math.abs(firstY - secondY) > 1.0E-7
                    || Math.abs(firstZ - secondZ) > 1.0E-7) {
                simplified.add(current);
            }
        }
        simplified.add(raw.getLast());
        return List.copyOf(simplified);
    }

    public record Route(BlockPos start, BlockPos target, List<BlockPos> path,
                        List<Vec3> renderPoints, long createdAtRefresh, double length) {
        private Route refreshedAt(long serial) {
            return new Route(start, target, path, renderPoints, serial, length);
        }
    }

    public record QueryStats(int matches, int routes, boolean searching, int itemCount,
                             int fresh, int aging, int stale, int frameOnly,
                             double nearestDistance, double routeDistance,
                             RouteFinder.FailureReason failureReason) {
        public static final QueryStats EMPTY = new QueryStats(
                0, 0, false, 0, 0, 0, 0, 0, Double.NaN, Double.NaN, null);
    }

    private static final class RouteJob {
        private final List<BlockPos> candidates;
        private final Set<BlockPos> candidateSet;
        private final boolean replacing;
        private final List<Route> found = new ArrayList<>();
        private BlockPos start;
        private RouteFinder.Search search;

        private RouteJob(BlockPos start, List<BlockPos> candidates, Set<BlockPos> candidateSet,
                         boolean replacing) {
            this.start = start.immutable();
            this.candidates = List.copyOf(candidates);
            this.candidateSet = candidateSet;
            this.replacing = replacing;
        }

        private boolean canContinue(BlockPos currentStart, Set<BlockPos> currentCandidates) {
            return isNearStart(start, currentStart) && candidateSet.equals(currentCandidates);
        }
    }

    private record FailedRouteAttempt(BlockPos start, Set<BlockPos> candidates, long createdAtRefresh) {
    }
}

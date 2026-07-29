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
    private static final int MAX_ROUTES_PER_COLOR = 3;
    private static final int FAILED_ROUTE_RETRY_REFRESHES = 10;
    private static final int ROUTE_NODE_BUDGET_PER_TICK = 800;
    private final Map<BlockPos, List<Integer>> matches = new LinkedHashMap<>();
    private final Map<Integer, List<Route>> routesByColor = new LinkedHashMap<>();
    private final Map<Integer, String> routeDiagnostics = new HashMap<>();
    private final Map<Integer, FailedRouteAttempt> failedRouteAttempts = new HashMap<>();
    private final Map<Integer, List<BlockPos>> latestCandidatesByColor = new LinkedHashMap<>();
    private final Map<Integer, Integer> latestItemCountsByColor = new LinkedHashMap<>();
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

        Map<BlockPos, Map<String, Integer>> itemsByStorage = new HashMap<>();
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
            Map<String, Integer> frameItems = itemsByStorage.computeIfAbsent(canonical,
                    ignored -> new LinkedHashMap<>());
            SearchSelection.itemIds(frame.getItem()).forEach(item -> frameItems.putIfAbsent(item, 0));
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
                record.normalizedCounts().forEach((item, count) -> remembered.merge(item, count, Math::max));
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
                    List<BlockPos> candidates = candidatesByColor.computeIfAbsent(
                            selected.getValue(), ignored -> new ArrayList<>());
                    if (!candidates.contains(storage.getKey())) {
                        candidates.add(storage.getKey());
                    }
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
                    routeJobs.containsKey(entry.getKey()), latestItemCountsByColor.getOrDefault(entry.getKey(), 0)));
        }
        return Map.copyOf(stats);
    }

    public static boolean isStorage(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }
        var block = minecraft.level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock
                || minecraft.level.getBlockEntity(pos) instanceof net.minecraft.world.Container;
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
        BlockPos start = RouteFinder.resolveStart(minecraft, minecraft.player.blockPosition());
        if (start == null) {
            routesByColor.clear();
            routeJobs.clear();
            return;
        }
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
                if (!rebased.isEmpty()) {
                    routesByColor.put(entry.getKey(), rebased);
                    if (rebased.size() == cached.size()
                            && refreshSerial - cached.getFirst().createdAtRefresh < 10) {
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
        BlockPos currentStart = RouteFinder.resolveStart(minecraft, minecraft.player.blockPosition());
        if (currentStart == null) {
            routeJobs.clear();
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

        List<Integer> colors = new ArrayList<>(routeJobs.keySet());
        int jobsToProcess = Math.min(colors.size(), ROUTE_NODE_BUDGET_PER_TICK);
        int baseBudget = ROUTE_NODE_BUDGET_PER_TICK / jobsToProcess;
        int extraBudget = ROUTE_NODE_BUDGET_PER_TICK % jobsToProcess;
        int startIndex = Math.floorMod(routeJobCursor, colors.size());
        for (int i = 0; i < jobsToProcess; i++) {
            Integer color = colors.get((startIndex + i) % colors.size());
            RouteJob job = routeJobs.get(color);
            if (job == null) {
                continue;
            }
            if (job.search == null) {
                job.search = RouteFinder.begin(minecraft, job.start, job.candidates,
                        MAX_ROUTES_PER_COLOR);
                if (job.search == null) {
                    finishRouteJob(color, job);
                    continue;
                }
            }

            int jobBudget = baseBudget + (i < extraBudget ? 1 : 0);
            RouteFinder.StepStatus status = job.search.step(minecraft, jobBudget);
            if (status == RouteFinder.StepStatus.FOUND) {
                RouteFinder.Result result = job.search.result();
                job.found.add(createRoute(minecraft, result.target(), result.path()));
                routesByColor.put(color, List.copyOf(job.found));
                if (job.search.completeAfterResult()) {
                    finishRouteJob(color, job);
                }
            } else if (status == RouteFinder.StepStatus.FAILED
                    || status == RouteFinder.StepStatus.COMPLETE) {
                finishRouteJob(color, job);
            }
        }
        routeJobCursor = (startIndex + jobsToProcess) % colors.size();
    }

    private boolean hasOutdatedRouteStart(BlockPos currentStart) {
        for (RouteJob job : routeJobs.values()) {
            if (!job.start.equals(currentStart)) {
                return true;
            }
        }
        for (List<Route> routes : routesByColor.values()) {
            if (!routes.isEmpty() && !routes.getFirst().start.equals(currentStart)) {
                return true;
            }
        }
        for (FailedRouteAttempt attempt : failedRouteAttempts.values()) {
            if (!attempt.start.equals(currentStart)) {
                return true;
            }
        }
        return false;
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
                continue;
            }
            int startIndex = route.path.indexOf(start);
            if (startIndex < 0) {
                continue;
            }
            List<BlockPos> remainingPath = List.copyOf(route.path.subList(startIndex, route.path.size()));
            if (!RouteFinder.isRouteValid(minecraft, route.target, remainingPath)) {
                continue;
            }
            rebased.add(new Route(start.immutable(), route.target, remainingPath,
                    buildRenderPoints(minecraft, remainingPath), route.createdAtRefresh));
        }
        return List.copyOf(rebased);
    }

    private Route createRoute(Minecraft minecraft, BlockPos target, List<BlockPos> path) {
        List<BlockPos> immutablePath = List.copyOf(path);
        return new Route(immutablePath.getFirst(), target.immutable(), immutablePath,
                buildRenderPoints(minecraft, immutablePath), refreshSerial);
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
                        List<Vec3> renderPoints, long createdAtRefresh) {
    }

    public record QueryStats(int matches, int routes, boolean searching, int itemCount) {
    }

    private static final class RouteJob {
        private final BlockPos start;
        private final List<BlockPos> candidates;
        private final List<Route> found = new ArrayList<>();
        private RouteFinder.Search search;

        private RouteJob(BlockPos start, List<BlockPos> candidates) {
            this.start = start.immutable();
            this.candidates = List.copyOf(candidates);
        }

        private boolean matches(BlockPos currentStart, List<BlockPos> currentCandidates) {
            return start.equals(currentStart) && candidates.equals(currentCandidates);
        }
    }

    private record FailedRouteAttempt(BlockPos start, List<BlockPos> candidates, long createdAtRefresh) {
    }
}

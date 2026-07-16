package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class RouteFinder {
    private static final int MAX_VISITED = 40_000;
    private static final int VERTICAL_MARGIN = 8;
    private static final double LOWEST_REACHABLE_SURFACE_OFFSET = -2.001;
    private static final double HIGHEST_REACHABLE_SURFACE_OFFSET = 0.501;

    private RouteFinder() {
    }

    public static Result findAny(Minecraft minecraft, BlockPos requestedStart, List<BlockPos> storages) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        if (storages.isEmpty()) {
            return null;
        }
        BlockPos start = findStart(minecraft, requestedStart);
        if (start == null) {
            return null;
        }

        Map<BlockPos, BlockPos> goalOwners = new HashMap<>();
        int minY = start.getY();
        int maxY = start.getY();
        for (BlockPos storage : storages) {
            minY = Math.min(minY, storage.getY());
            maxY = Math.max(maxY, storage.getY());
            for (BlockPos goal : findGoals(minecraft, storage)) {
                if (StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), goal)) {
                    goalOwners.putIfAbsent(goal, storage);
                }
            }
        }
        if (goalOwners.isEmpty()) {
            return null;
        }
        minY -= VERTICAL_MARGIN;
        maxY += VERTICAL_MARGIN;

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimatedTotal, b.estimatedTotal));
        Map<BlockPos, Double> bestCost = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        bestCost.put(start, 0.0);
        open.add(new Node(start, heuristic(start, goalOwners.keySet())));

        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_VISITED) {
            Node node = open.poll();
            BlockPos current = node.pos;
            if (!closed.add(current)) {
                continue;
            }
            BlockPos target = goalOwners.get(current);
            if (target != null) {
                return new Result(target, reconstruct(cameFrom, current));
            }

            double currentCost = bestCost.getOrDefault(current, Double.POSITIVE_INFINITY);
            for (BlockPos next : neighbours(minecraft, current, start, minY, maxY)) {
                if (closed.contains(next)) {
                    continue;
                }
                double rise = Math.max(0.0, surfaceY(minecraft, next) - surfaceY(minecraft, current));
                double stepCost = 1.0 + rise * 0.25;
                double candidateCost = currentCost + stepCost;
                if (candidateCost < bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    bestCost.put(next, candidateCost);
                    cameFrom.put(next, current);
                    open.add(new Node(next, candidateCost + heuristic(next, goalOwners.keySet())));
                }
            }
        }
        return null;
    }

    public static boolean isPathValid(Minecraft minecraft, List<BlockPos> path) {
        if (minecraft.player == null || minecraft.level == null || path.isEmpty()) {
            return false;
        }
        BlockPos previous = null;
        double previousSurface = Double.NaN;
        for (BlockPos pos : path) {
            double surface = surfaceY(minecraft, pos);
            if (!StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), pos)
                    || !Double.isFinite(surface) || !isStandableAt(minecraft, pos, surface)) {
                return false;
            }
            if (previous != null) {
                int horizontalDistance = Math.abs(pos.getX() - previous.getX()) + Math.abs(pos.getZ() - previous.getZ());
                double heightDifference = surface - previousSurface;
                if (horizontalDistance != 1 || heightDifference > 1.001 || heightDifference < -1.251) {
                    return false;
                }
            }
            previous = pos;
            previousSurface = surface;
        }
        return true;
    }

    public static boolean isRouteValid(Minecraft minecraft, BlockPos storage, List<BlockPos> path) {
        return isPathValid(minecraft, path)
                && findGoals(minecraft, storage).contains(path.getLast());
    }

    private static List<BlockPos> neighbours(Minecraft minecraft, BlockPos current, BlockPos start, int minY, int maxY) {
        List<BlockPos> result = new ArrayList<>(4);
        double currentSurface = surfaceY(minecraft, current);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            BlockPos[] candidates = {horizontal, horizontal.above(), horizontal.below()};
            for (BlockPos candidate : candidates) {
                double candidateSurface = surfaceY(minecraft, candidate);
                double heightDifference = candidateSurface - currentSurface;
                if (candidate.getY() >= minY && candidate.getY() <= maxY
                        && StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), candidate)
                        && Double.isFinite(candidateSurface)
                        && heightDifference <= 1.001
                        && heightDifference >= -1.251
                        && isStandableAt(minecraft, candidate, candidateSurface)) {
                    result.add(candidate.immutable());
                    break;
                }
            }
        }
        return result;
    }

    private static BlockPos findStart(Minecraft minecraft, BlockPos requested) {
        if (isStandable(minecraft, requested)) {
            return requested.immutable();
        }
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos candidate = requested.offset(0, dy, 0);
            if (isStandable(minecraft, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private static Set<BlockPos> findGoals(Minecraft minecraft, BlockPos storage) {
        Set<BlockPos> goals = new HashSet<>();
        Set<BlockPos> storageBlocks = new HashSet<>(StorageIndex.physicalBlocks(minecraft, storage));
        for (BlockPos physical : storageBlocks) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = physical.relative(direction);
                if (storageBlocks.contains(side)) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = side.offset(0, dy, 0);
                    double surface = surfaceY(minecraft, candidate);
                    double surfaceOffset = surface - physical.getY();
                    if (Double.isFinite(surface)
                            && surfaceOffset >= LOWEST_REACHABLE_SURFACE_OFFSET
                            && surfaceOffset <= HIGHEST_REACHABLE_SURFACE_OFFSET
                            && isStandableAt(minecraft, candidate, surface)) {
                        goals.add(candidate.immutable());
                    }
                }
            }
        }
        return goals;
    }

    private static boolean isStandable(Minecraft minecraft, BlockPos feet) {
        double surface = surfaceY(minecraft, feet);
        return Double.isFinite(surface) && isStandableAt(minecraft, feet, surface);
    }

    private static boolean isStandableAt(Minecraft minecraft, BlockPos feet, double surface) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !hasChunk(level, feet)) {
            return false;
        }

        if (StorageFinderConfig.current().avoidHazards
                && (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                || isHazardous(level.getBlockState(feet).getBlock())
                || isHazardous(level.getBlockState(feet.below()).getBlock()))) {
            return false;
        }

        double width = minecraft.player.getBbWidth();
        double height = minecraft.player.getBbHeight();
        double centerX = feet.getX() + 0.5;
        double centerZ = feet.getZ() + 0.5;
        double bottom = surface + 0.0001;
        AABB body = new AABB(centerX - width / 2.0, bottom, centerZ - width / 2.0,
                centerX + width / 2.0, bottom + height, centerZ + width / 2.0);
        return level.noCollision(minecraft.player, body);
    }

    public static double surfaceY(Minecraft minecraft, BlockPos feet) {
        ClientLevel level = minecraft.level;
        if (level == null || !hasChunk(level, feet)) {
            return Double.NaN;
        }

        double inCell = collisionTop(level, feet);
        if (Double.isFinite(inCell)) {
            return inCell;
        }
        return collisionTop(level, feet.below());
    }

    private static double collisionTop(ClientLevel level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        double localTop = shape.max(Direction.Axis.Y, 0.5, 0.5);
        if (!Double.isFinite(localTop)) {
            localTop = shape.max(Direction.Axis.Y);
        }
        return pos.getY() + localTop;
    }

    private static boolean hasChunk(ClientLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean isHazardous(Block block) {
        return block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK
                || block == Blocks.FIRE || block == Blocks.SOUL_FIRE
                || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.WITHER_ROSE
                || block == Blocks.POWDER_SNOW;
    }

    private static double heuristic(BlockPos pos, Collection<BlockPos> goals) {
        if (goals.size() > 32) {
            return 0.0;
        }
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos goal : goals) {
            double distance = Math.abs(pos.getX() - goal.getX())
                    + Math.abs(pos.getZ() - goal.getZ());
            best = Math.min(best, distance);
        }
        return best;
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private record Node(BlockPos pos, double estimatedTotal) {
    }

    public record Result(BlockPos target, List<BlockPos> path) {
    }
}

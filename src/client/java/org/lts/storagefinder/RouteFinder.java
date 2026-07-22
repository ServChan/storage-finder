package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    private static final double MAX_STEP_UP = 1.001;
    private static final double MAX_DROP_DOWN = 1.251;

    private RouteFinder() {
    }

    public static Search begin(Minecraft minecraft, BlockPos requestedStart, List<BlockPos> storages) {
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

        return new Search(minecraft.level, start, goalOwners, minY, maxY);
    }

    public static final class Search {
        private final ClientLevel level;
        private final BlockPos start;
        private final Map<BlockPos, BlockPos> goalOwners;
        private final int minY;
        private final int maxY;
        private final PriorityQueue<Node> open = new PriorityQueue<>(
                (a, b) -> Double.compare(a.estimatedTotal, b.estimatedTotal));
        private final Map<BlockPos, Double> bestCost = new HashMap<>();
        private final Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        private final Set<BlockPos> closed = new HashSet<>();
        private int visited;
        private StepStatus status = StepStatus.RUNNING;
        private Result result;

        private Search(ClientLevel level, BlockPos start, Map<BlockPos, BlockPos> goalOwners,
                       int minY, int maxY) {
            this.level = level;
            this.start = start;
            this.goalOwners = Map.copyOf(goalOwners);
            this.minY = minY;
            this.maxY = maxY;
            bestCost.put(start, 0.0);
            open.add(new Node(start, heuristic(start, goalOwners.keySet())));
        }

        public StepStatus step(Minecraft minecraft, int nodeBudget) {
            if (status != StepStatus.RUNNING) {
                return status;
            }
            if (minecraft.level != level || minecraft.player == null) {
                status = StepStatus.FAILED;
                return status;
            }

            int processed = 0;
            int budget = Math.max(1, nodeBudget);
            while (!open.isEmpty() && visited < MAX_VISITED && processed++ < budget) {
                visited++;
                Node node = open.poll();
                BlockPos current = node.pos;
                if (!closed.add(current)) {
                    continue;
                }
                BlockPos target = goalOwners.get(current);
                if (target != null) {
                    result = new Result(target, reconstruct(cameFrom, current));
                    status = StepStatus.FOUND;
                    return status;
                }

                double currentCost = bestCost.getOrDefault(current, Double.POSITIVE_INFINITY);
                for (BlockPos next : neighbours(minecraft, current, minY, maxY)) {
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
            if (open.isEmpty() || visited >= MAX_VISITED) {
                status = StepStatus.FAILED;
            }
            return status;
        }

        public Result result() {
            return result;
        }
    }

    public enum StepStatus {
        RUNNING,
        FOUND,
        FAILED
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
                boolean verticalClimb = horizontalDistance == 0
                        && Math.abs(pos.getY() - previous.getY()) == 1
                        && canClimbBetween(minecraft, previous, pos);
                if ((!verticalClimb && horizontalDistance != 1)
                        || heightDifference > MAX_STEP_UP || heightDifference < -MAX_DROP_DOWN
                        || !canTraverse(minecraft, previous, previousSurface, pos, surface)) {
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

    private static List<BlockPos> neighbours(Minecraft minecraft, BlockPos current, int minY, int maxY) {
        List<BlockPos> result = new ArrayList<>(6);
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
                        && heightDifference <= MAX_STEP_UP
                        && heightDifference >= -MAX_DROP_DOWN
                        && isStandableAt(minecraft, candidate, candidateSurface)
                        && canTraverse(minecraft, current, currentSurface, candidate, candidateSurface)) {
                    result.add(candidate.immutable());
                    break;
                }
            }
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.offset(0, dy, 0);
            double candidateSurface = surfaceY(minecraft, candidate);
            if (candidate.getY() >= minY && candidate.getY() <= maxY
                    && StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), candidate)
                    && Double.isFinite(candidateSurface)
                    && isStandableAt(minecraft, candidate, candidateSurface)
                    && canClimbBetween(minecraft, current, candidate)
                    && canTraverse(minecraft, current, currentSurface, candidate, candidateSurface)) {
                result.add(candidate.immutable());
            }
        }
        return result;
    }

    private static BlockPos findStart(Minecraft minecraft, BlockPos requested) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dy : new int[]{0, -1, 1, -2, 2}) {
            BlockPos candidate = requested.offset(0, dy, 0);
            double surface = surfaceY(minecraft, candidate);
            if (Double.isFinite(surface) && isStandableAt(minecraft, candidate, surface)) {
                double distance = Math.abs(surface - minecraft.player.getY());
                if (distance < bestDistance) {
                    best = candidate.immutable();
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static Set<BlockPos> findGoals(Minecraft minecraft, BlockPos storage) {
        Set<BlockPos> goals = new HashSet<>();
        if (minecraft.level == null || minecraft.player == null) {
            return goals;
        }
        Set<BlockPos> storageBlocks = new HashSet<>(StorageIndex.physicalBlocks(minecraft, storage));
        double reach = minecraft.player.blockInteractionRange();
        int horizontalRange = Math.max(1, (int) Math.ceil(reach));
        for (BlockPos physical : storageBlocks) {
            BlockState storageState = minecraft.level.getBlockState(physical);
            boolean walkableBarrelTop = storageState.getBlock() instanceof BarrelBlock
                    && storageState.getValue(BarrelBlock.FACING) == Direction.UP;
            for (int dx = -horizontalRange; dx <= horizontalRange; dx++) {
                for (int dz = -horizontalRange; dz <= horizontalRange; dz++) {
                    if (dx == 0 && dz == 0 && !walkableBarrelTop) {
                        continue;
                    }
                    if (dx * dx + dz * dz > (reach + 1.0) * (reach + 1.0)) {
                        continue;
                    }
                    for (int dy = -horizontalRange; dy <= 2; dy++) {
                        BlockPos candidate = physical.offset(dx, dy, dz);
                        if (storageBlocks.contains(candidate)) {
                            continue;
                        }
                        double surface = surfaceY(minecraft, candidate);
                        if (Double.isFinite(surface)
                                && isStandableAt(minecraft, candidate, surface)
                                && canReachStorage(minecraft, candidate, surface, physical, reach)) {
                            goals.add(candidate.immutable());
                        }
                    }
                }
            }
        }
        return goals;
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

        return level.noCollision(minecraft.player, bodyBox(minecraft, feet, surface));
    }

    public static double surfaceY(Minecraft minecraft, BlockPos feet) {
        ClientLevel level = minecraft.level;
        if (level == null || !hasChunk(level, feet)) {
            return Double.NaN;
        }

        if (isClimbable(level, feet)) {
            return feet.getY();
        }
        if (isClimbable(level, feet.below())) {
            return feet.getY();
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
        return Double.isFinite(localTop) ? pos.getY() + localTop : Double.NaN;
    }

    private static boolean canTraverse(Minecraft minecraft, BlockPos from, double fromSurface,
                                       BlockPos to, double toSurface) {
        if (!Double.isFinite(fromSurface) || !Double.isFinite(toSurface)
                || minecraft.level == null || minecraft.player == null) {
            return false;
        }
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            if (!canClimbBetween(minecraft, from, to)) {
                return false;
            }
            double bottom = Math.min(fromSurface, toSurface) + 0.0001;
            double top = Math.max(fromSurface, toSurface) + minecraft.player.getBbHeight();
            AABB vertical = bodyBox(minecraft, from, bottom - 0.0001)
                    .setMaxY(top);
            return minecraft.level.noCollision(minecraft.player, vertical);
        }

        double travelSurface = Math.max(fromSurface, toSurface);
        AABB fromBody = bodyBox(minecraft, from, travelSurface);
        AABB toBody = bodyBox(minecraft, to, travelSurface);
        AABB swept = fromBody.minmax(toBody);
        return minecraft.level.noCollision(minecraft.player, swept);
    }

    private static AABB bodyBox(Minecraft minecraft, BlockPos feet, double surface) {
        double width = minecraft.player.getBbWidth();
        double height = minecraft.player.getBbHeight();
        double centerX = feet.getX() + 0.5;
        double centerZ = feet.getZ() + 0.5;
        double bottom = surface + 0.0001;
        return new AABB(centerX - width / 2.0, bottom, centerZ - width / 2.0,
                centerX + width / 2.0, bottom + height, centerZ + width / 2.0);
    }

    private static boolean canClimbBetween(Minecraft minecraft, BlockPos first, BlockPos second) {
        if (minecraft.level == null || first.getX() != second.getX() || first.getZ() != second.getZ()) {
            return false;
        }
        return isClimbable(minecraft.level, first) || isClimbable(minecraft.level, second);
    }

    private static boolean isClimbable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.SCAFFOLDING);
    }

    private static boolean canReachStorage(Minecraft minecraft, BlockPos feet, double surface,
                                           BlockPos storage, double reach) {
        Vec3 eye = new Vec3(feet.getX() + 0.5, surface + minecraft.player.getEyeHeight(),
                feet.getZ() + 0.5);
        AABB targetBox = new AABB(storage);
        double x = Math.max(targetBox.minX, Math.min(eye.x, targetBox.maxX));
        double y = Math.max(targetBox.minY, Math.min(eye.y, targetBox.maxY));
        double z = Math.max(targetBox.minZ, Math.min(eye.z, targetBox.maxZ));
        Vec3 nearest = new Vec3(x, y, z);
        if (eye.distanceToSqr(nearest) > reach * reach) {
            return false;
        }
        Vec3 center = targetBox.getCenter();
        Vec3 aim = nearest.add(center.subtract(nearest).scale(0.01));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, aim,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getBlockPos().equals(storage);
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

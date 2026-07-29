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
    private static final double DIAGONAL_COST = Math.sqrt(2.0);
    private static final int DIAGONAL_TRAVERSE_SAMPLES = 3;
    private static final int[][] HORIZONTAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private RouteFinder() {
    }

    public static BlockPos resolveStart(Minecraft minecraft, BlockPos requestedStart) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        return findStart(minecraft, requestedStart, new HashMap<>());
    }

    public static Search begin(Minecraft minecraft, BlockPos requestedStart, List<BlockPos> storages,
                               int targetLimit) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        if (storages.isEmpty() || targetLimit < 1) {
            return null;
        }
        Map<BlockPos, Cell> cells = new HashMap<>();
        BlockPos start = findStart(minecraft, requestedStart, cells);
        if (start == null) {
            return null;
        }

        Map<BlockPos, BlockPos> goalOwners = new HashMap<>();
        int minY = start.getY();
        int maxY = start.getY();
        for (BlockPos storage : storages) {
            minY = Math.min(minY, storage.getY());
            maxY = Math.max(maxY, storage.getY());
            for (BlockPos goal : findGoals(minecraft, storage, cells)) {
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

        return new Search(minecraft.level, start, goalOwners, minY, maxY,
                Math.min(targetLimit, new HashSet<>(goalOwners.values()).size()), cells);
    }

    public static final class Search {
        private final ClientLevel level;
        private final BlockPos start;
        private final Map<BlockPos, BlockPos> goalOwners;
        private final int minY;
        private final int maxY;
        private final int targetLimit;
        private final PriorityQueue<Node> open = new PriorityQueue<>(
                (a, b) -> {
                    int estimated = Double.compare(a.estimatedTotal, b.estimatedTotal);
                    return estimated != 0 ? estimated : Double.compare(a.cost, b.cost);
                });
        private final Map<BlockPos, Double> bestCost = new HashMap<>();
        private final Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        private final Set<BlockPos> closed = new HashSet<>();
        private final Map<BlockPos, Cell> cells;
        private GoalHeuristic heuristic;
        private int visited;
        private int foundTargets;
        private boolean completeAfterResult;
        private StepStatus status = StepStatus.RUNNING;
        private Result result;

        private Search(ClientLevel level, BlockPos start, Map<BlockPos, BlockPos> goalOwners,
                       int minY, int maxY, int targetLimit, Map<BlockPos, Cell> cells) {
            this.level = level;
            this.start = start;
            this.goalOwners = new HashMap<>(goalOwners);
            this.minY = minY;
            this.maxY = maxY;
            this.targetLimit = targetLimit;
            this.cells = cells;
            this.heuristic = GoalHeuristic.from(goalOwners);
            bestCost.put(start, 0.0);
            open.add(new Node(start, 0.0, heuristic.distance(start)));
        }

        public StepStatus step(Minecraft minecraft, int nodeBudget) {
            if (status == StepStatus.FOUND) {
                if (completeAfterResult) {
                    status = StepStatus.COMPLETE;
                    return status;
                }
                status = StepStatus.RUNNING;
                result = null;
            } else if (status != StepStatus.RUNNING) {
                return status;
            }
            if (minecraft.level != level || minecraft.player == null) {
                status = StepStatus.FAILED;
                return status;
            }

            int processed = 0;
            int budget = Math.max(1, nodeBudget);
            while (!open.isEmpty() && visited < MAX_VISITED && processed++ < budget) {
                Node node = open.poll();
                BlockPos current = node.pos;
                double currentCost = bestCost.getOrDefault(current, Double.POSITIVE_INFINITY);
                if (node.cost > currentCost + 1.0E-7) {
                    continue;
                }
                if (!closed.add(current)) {
                    continue;
                }
                visited++;
                BlockPos target = goalOwners.get(current);
                if (target != null) {
                    result = new Result(target, reconstruct(cameFrom, current));
                    foundTargets++;
                    goalOwners.entrySet().removeIf(entry -> entry.getValue().equals(target));
                    completeAfterResult = foundTargets >= targetLimit || goalOwners.isEmpty();
                    if (!completeAfterResult) {
                        heuristic = GoalHeuristic.from(goalOwners);
                        rebuildOpen();
                    }
                    status = StepStatus.FOUND;
                    return status;
                }

                Cell currentCell = cell(minecraft, current, cells);
                for (BlockPos next : neighbours(minecraft, current, currentCell, minY, maxY, cells)) {
                    if (closed.contains(next)) {
                        continue;
                    }
                    double rise = Math.max(0.0, cell(minecraft, next, cells).surface - currentCell.surface);
                    double stepCost = movementCost(current, next) + rise * 0.25;
                    double candidateCost = currentCost + stepCost;
                    if (candidateCost < bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                        bestCost.put(next, candidateCost);
                        cameFrom.put(next, current);
                        open.add(new Node(next, candidateCost,
                                candidateCost + heuristic.distance(next)));
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

        public boolean completeAfterResult() {
            return completeAfterResult;
        }

        private void rebuildOpen() {
            open.clear();
            for (Map.Entry<BlockPos, Double> entry : bestCost.entrySet()) {
                if (!closed.contains(entry.getKey())) {
                    open.add(new Node(entry.getKey(), entry.getValue(),
                            entry.getValue() + heuristic.distance(entry.getKey())));
                }
            }
        }
    }

    public enum StepStatus {
        RUNNING,
        FOUND,
        COMPLETE,
        FAILED
    }

    public static boolean isPathValid(Minecraft minecraft, List<BlockPos> path) {
        if (minecraft.player == null || minecraft.level == null || path.isEmpty()) {
            return false;
        }
        Map<BlockPos, Cell> cells = new HashMap<>();
        BlockPos previous = null;
        double previousSurface = Double.NaN;
        for (BlockPos pos : path) {
            Cell pathCell = cell(minecraft, pos, cells);
            double surface = pathCell.surface;
            if (!StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), pos)
                    || !Double.isFinite(surface) || !pathCell.standable) {
                return false;
            }
            if (previous != null) {
                int dx = Math.abs(pos.getX() - previous.getX());
                int dz = Math.abs(pos.getZ() - previous.getZ());
                double heightDifference = surface - previousSurface;
                boolean verticalClimb = dx == 0 && dz == 0
                        && Math.abs(pos.getY() - previous.getY()) == 1
                        && canClimbBetween(minecraft, previous, pos);
                boolean horizontalStep = (dx != 0 || dz != 0) && dx <= 1 && dz <= 1;
                if ((!verticalClimb && !horizontalStep)
                        || heightDifference > MAX_STEP_UP || heightDifference < -MAX_DROP_DOWN
                        || (dx == 1 && dz == 1
                        && !hasDiagonalSupport(minecraft, previous, previousSurface, pos, surface,
                        minecraft.level.getMinY(), minecraft.level.getMaxY(), cells))
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
        if (!isPathValid(minecraft, path) || minecraft.level == null || minecraft.player == null) {
            return false;
        }
        BlockPos end = path.getLast();
        Set<BlockPos> storageBlocks = new HashSet<>(StorageIndex.physicalBlocks(minecraft, storage));
        if (storageBlocks.contains(end)) {
            return false;
        }
        double surface = surfaceY(minecraft, end);
        double reach = minecraft.player.blockInteractionRange();
        for (BlockPos physical : storageBlocks) {
            if (isAdjacentGoal(minecraft, end, physical)
                    && canReachStorage(minecraft, end, surface, physical, reach)) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> neighbours(Minecraft minecraft, BlockPos current, Cell currentCell,
                                             int minY, int maxY, Map<BlockPos, Cell> cells) {
        List<BlockPos> result = new ArrayList<>(10);
        double currentSurface = currentCell.surface;
        for (int[] offset : HORIZONTAL_OFFSETS) {
            boolean diagonal = offset[0] != 0 && offset[1] != 0;
            BlockPos horizontal = current.offset(offset[0], 0, offset[1]);
            BlockPos[] candidates = {horizontal, horizontal.above(), horizontal.below()};
            for (BlockPos candidate : candidates) {
                Cell candidateCell = cell(minecraft, candidate, cells);
                if (!Double.isFinite(candidateCell.surface)) {
                    continue;
                }
                BlockPos next = canonicalFeet(candidate, candidateCell.surface);
                Cell nextCell = cell(minecraft, next, cells);
                double candidateSurface = nextCell.surface;
                double heightDifference = candidateSurface - currentSurface;
                if (next.getY() >= minY && next.getY() <= maxY
                        && StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), next)
                        && Double.isFinite(candidateSurface)
                        && heightDifference <= MAX_STEP_UP
                        && heightDifference >= -MAX_DROP_DOWN
                        && nextCell.standable
                        && (!diagonal || hasDiagonalSupport(minecraft, current, currentSurface,
                        next, candidateSurface, minY, maxY, cells))
                        && canTraverse(minecraft, current, currentSurface, next, candidateSurface)) {
                    if (!result.contains(next)) {
                        result.add(next);
                    }
                    break;
                }
            }
        }
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = current.offset(0, dy, 0);
            Cell candidateCell = cell(minecraft, candidate, cells);
            if (!Double.isFinite(candidateCell.surface)) {
                continue;
            }
            BlockPos next = canonicalFeet(candidate, candidateCell.surface);
            Cell nextCell = cell(minecraft, next, cells);
            double candidateSurface = nextCell.surface;
            if (next.getY() >= minY && next.getY() <= maxY
                    && StorageLocator.withinSearchRadius(minecraft.player.getX(), minecraft.player.getZ(), next)
                    && Double.isFinite(candidateSurface)
                    && nextCell.standable
                    && canClimbBetween(minecraft, current, next)
                    && canTraverse(minecraft, current, currentSurface, next, candidateSurface)
                    && !result.contains(next)) {
                result.add(next);
            }
        }
        return result;
    }

    private static boolean hasDiagonalSupport(Minecraft minecraft, BlockPos from,
                                              double fromSurface, BlockPos to,
                                              double toSurface, int minY, int maxY,
                                              Map<BlockPos, Cell> cells) {
        BlockPos xSide = findCardinalStep(minecraft, from, fromSurface,
                to.getX(), from.getZ(), minY, maxY, cells);
        if (xSide == null) {
            return false;
        }
        BlockPos zSide = findCardinalStep(minecraft, from, fromSurface,
                from.getX(), to.getZ(), minY, maxY, cells);
        if (zSide == null) {
            return false;
        }
        return canFinishCardinalStep(minecraft, xSide, to, toSurface, cells)
                && canFinishCardinalStep(minecraft, zSide, to, toSurface, cells);
    }

    private static BlockPos findCardinalStep(Minecraft minecraft, BlockPos from,
                                             double fromSurface, int targetX, int targetZ,
                                             int minY, int maxY, Map<BlockPos, Cell> cells) {
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos sampled = new BlockPos(targetX, from.getY() + dy, targetZ);
            Cell sampledCell = cell(minecraft, sampled, cells);
            if (!Double.isFinite(sampledCell.surface)) {
                continue;
            }
            BlockPos normalized = canonicalFeet(sampled, sampledCell.surface);
            Cell normalizedCell = cell(minecraft, normalized, cells);
            double difference = normalizedCell.surface - fromSurface;
            if (normalized.getY() >= minY && normalized.getY() <= maxY
                    && StorageLocator.withinSearchRadius(
                    minecraft.player.getX(), minecraft.player.getZ(), normalized)
                    && normalizedCell.standable
                    && difference <= MAX_STEP_UP && difference >= -MAX_DROP_DOWN
                    && canTraverse(minecraft, from, fromSurface,
                    normalized, normalizedCell.surface)) {
                return normalized;
            }
        }
        return null;
    }

    private static boolean canFinishCardinalStep(Minecraft minecraft, BlockPos from,
                                                 BlockPos to, double toSurface,
                                                 Map<BlockPos, Cell> cells) {
        if (Math.abs(from.getX() - to.getX()) + Math.abs(from.getZ() - to.getZ()) != 1) {
            return false;
        }
        double fromSurface = cell(minecraft, from, cells).surface;
        double difference = toSurface - fromSurface;
        return Double.isFinite(fromSurface)
                && difference <= MAX_STEP_UP && difference >= -MAX_DROP_DOWN
                && canTraverse(minecraft, from, fromSurface, to, toSurface);
    }

    private static BlockPos findStart(Minecraft minecraft, BlockPos requested, Map<BlockPos, Cell> cells) {
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int dy : new int[]{0, -1, 1, -2, 2}) {
            BlockPos candidate = requested.offset(0, dy, 0);
            Cell candidateCell = cell(minecraft, candidate, cells);
            if (Double.isFinite(candidateCell.surface)) {
                BlockPos normalized = canonicalFeet(candidate, candidateCell.surface);
                Cell normalizedCell = cell(minecraft, normalized, cells);
                double distance = Math.abs(normalizedCell.surface - minecraft.player.getY());
                if (!normalizedCell.standable || !Double.isFinite(normalizedCell.surface)) {
                    continue;
                }
                if (distance < bestDistance) {
                    best = normalized;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static Set<BlockPos> findGoals(Minecraft minecraft, BlockPos storage,
                                           Map<BlockPos, Cell> cells) {
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
            for (int[] offset : HORIZONTAL_OFFSETS) {
                BlockPos adjacent = physical.offset(offset[0], 0, offset[1]);
                for (int dy = -horizontalRange; dy <= 2; dy++) {
                    addGoalIfReachable(minecraft, adjacent.offset(0, dy, 0), physical,
                            storageBlocks, reach, cells, goals);
                }
            }
            if (walkableBarrelTop) {
                addGoalIfReachable(minecraft, physical.above(), physical,
                        storageBlocks, reach, cells, goals);
            }
        }
        return goals;
    }

    private static void addGoalIfReachable(Minecraft minecraft, BlockPos candidate,
                                           BlockPos physical, Set<BlockPos> storageBlocks,
                                           double reach, Map<BlockPos, Cell> cells,
                                           Set<BlockPos> goals) {
        if (storageBlocks.contains(candidate)) {
            return;
        }
        Cell candidateCell = cell(minecraft, candidate, cells);
        if (!Double.isFinite(candidateCell.surface)) {
            return;
        }
        BlockPos normalized = canonicalFeet(candidate, candidateCell.surface);
        if (storageBlocks.contains(normalized)) {
            return;
        }
        Cell normalizedCell = cell(minecraft, normalized, cells);
        if (Double.isFinite(normalizedCell.surface)
                && normalizedCell.standable
                && canReachStorage(minecraft, normalized, normalizedCell.surface, physical, reach)) {
            goals.add(normalized);
        }
    }

    private static BlockPos canonicalFeet(BlockPos sampledCell, double surface) {
        int feetY = (int) Math.floor(surface + 1.0E-4);
        return new BlockPos(sampledCell.getX(), feetY, sampledCell.getZ());
    }

    private static Cell cell(Minecraft minecraft, BlockPos pos, Map<BlockPos, Cell> cells) {
        return cells.computeIfAbsent(pos.immutable(), key -> {
            double surface = surfaceY(minecraft, key);
            return new Cell(surface, Double.isFinite(surface) && isStandableAt(minecraft, key, surface));
        });
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
        double footprintRadius = minecraft.player == null
                ? 0.0 : Math.max(0.0, minecraft.player.getBbWidth() / 2.0 - 1.0E-4);
        double inCell = collisionTop(level, feet, footprintRadius);
        if (Double.isFinite(inCell)) {
            return inCell;
        }
        return collisionTop(level, feet.below(), footprintRadius);
    }

    private static double collisionTop(ClientLevel level, BlockPos pos, double footprintRadius) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        double localTop = Double.NaN;
        int minSample = footprintRadius > 0.0 ? -1 : 0;
        int maxSample = footprintRadius > 0.0 ? 1 : 0;
        for (int xSample = minSample; xSample <= maxSample; xSample++) {
            for (int zSample = minSample; zSample <= maxSample; zSample++) {
                double sampleX = Math.max(1.0E-4,
                        Math.min(0.9999, 0.5 + xSample * footprintRadius));
                double sampleZ = Math.max(1.0E-4,
                        Math.min(0.9999, 0.5 + zSample * footprintRadius));
                double sampledTop = shape.max(Direction.Axis.Y, sampleX, sampleZ);
                if (Double.isFinite(sampledTop)) {
                    localTop = Double.isFinite(localTop)
                            ? Math.max(localTop, sampledTop) : sampledTop;
                }
            }
        }
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
        boolean diagonal = from.getX() != to.getX() && from.getZ() != to.getZ();
        if (!diagonal) {
            AABB fromBody = bodyBox(minecraft, from, travelSurface);
            AABB toBody = bodyBox(minecraft, to, travelSurface);
            return minecraft.level.noCollision(minecraft.player, fromBody.minmax(toBody));
        }

        double fromX = from.getX() + 0.5;
        double fromZ = from.getZ() + 0.5;
        double toX = to.getX() + 0.5;
        double toZ = to.getZ() + 0.5;
        int firstSample = Math.abs(fromSurface - toSurface) < 1.0E-7 ? 1 : 0;
        int lastSample = firstSample == 1 ? DIAGONAL_TRAVERSE_SAMPLES - 1
                : DIAGONAL_TRAVERSE_SAMPLES;
        for (int sample = firstSample; sample <= lastSample; sample++) {
            double progress = sample / (double) DIAGONAL_TRAVERSE_SAMPLES;
            double centerX = fromX + (toX - fromX) * progress;
            double centerZ = fromZ + (toZ - fromZ) * progress;
            if (!minecraft.level.noCollision(minecraft.player,
                    bodyBoxAt(minecraft, centerX, centerZ, travelSurface))) {
                return false;
            }
        }
        return true;
    }

    private static AABB bodyBox(Minecraft minecraft, BlockPos feet, double surface) {
        return bodyBoxAt(minecraft, feet.getX() + 0.5, feet.getZ() + 0.5, surface);
    }

    private static AABB bodyBoxAt(Minecraft minecraft, double centerX, double centerZ,
                                  double surface) {
        double width = minecraft.player.getBbWidth();
        double height = minecraft.player.getBbHeight();
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

    private static boolean isAdjacentGoal(Minecraft minecraft, BlockPos feet, BlockPos storage) {
        int dx = Math.abs(feet.getX() - storage.getX());
        int dz = Math.abs(feet.getZ() - storage.getZ());
        if ((dx != 0 || dz != 0) && dx <= 1 && dz <= 1) {
            return true;
        }
        if (dx != 0 || dz != 0 || minecraft.level == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(storage);
        return state.getBlock() instanceof BarrelBlock
                && state.getValue(BarrelBlock.FACING) == Direction.UP;
    }

    private static double movementCost(BlockPos from, BlockPos to) {
        return from.getX() != to.getX() && from.getZ() != to.getZ()
                ? DIAGONAL_COST : 1.0;
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

    private static final class GoalHeuristic {
        private static final int MAX_INDIVIDUAL_BOUNDS = 64;
        private final List<Bounds> bounds;

        private GoalHeuristic(List<Bounds> bounds) {
            this.bounds = bounds;
        }

        private static GoalHeuristic from(Map<BlockPos, BlockPos> goalOwners) {
            Map<BlockPos, Bounds> byOwner = new HashMap<>();
            Bounds all = null;
            for (Map.Entry<BlockPos, BlockPos> entry : goalOwners.entrySet()) {
                BlockPos goal = entry.getKey();
                byOwner.compute(entry.getValue(), (ignored, bounds) ->
                        bounds == null ? Bounds.at(goal) : bounds.include(goal));
                all = all == null ? Bounds.at(goal) : all.include(goal);
            }
            if (byOwner.size() <= MAX_INDIVIDUAL_BOUNDS) {
                return new GoalHeuristic(List.copyOf(byOwner.values()));
            }
            return new GoalHeuristic(List.of(all));
        }

        private double distance(BlockPos pos) {
            double best = Double.POSITIVE_INFINITY;
            for (Bounds bound : bounds) {
                best = Math.min(best, bound.distance(pos));
            }
            return best;
        }
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

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        private static Bounds at(BlockPos pos) {
            return new Bounds(pos.getX(), pos.getX(), pos.getZ(), pos.getZ());
        }

        private Bounds include(BlockPos pos) {
            return new Bounds(Math.min(minX, pos.getX()), Math.max(maxX, pos.getX()),
                    Math.min(minZ, pos.getZ()), Math.max(maxZ, pos.getZ()));
        }

        private double distance(BlockPos pos) {
            int dx = pos.getX() < minX ? minX - pos.getX()
                    : pos.getX() > maxX ? pos.getX() - maxX : 0;
            int dz = pos.getZ() < minZ ? minZ - pos.getZ()
                    : pos.getZ() > maxZ ? pos.getZ() - maxZ : 0;
            int diagonal = Math.min(dx, dz);
            int straight = Math.max(dx, dz) - diagonal;
            return diagonal * DIAGONAL_COST + straight;
        }
    }

    private record Cell(double surface, boolean standable) {
    }

    private record Node(BlockPos pos, double cost, double estimatedTotal) {
    }

    public record Result(BlockPos target, List<BlockPos> path) {
    }
}

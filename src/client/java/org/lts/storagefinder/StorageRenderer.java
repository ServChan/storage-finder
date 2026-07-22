package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class StorageRenderer {
    private StorageRenderer() {
    }

    public static void render(StorageLocator locator) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        StorageFinderConfig config = StorageFinderConfig.current();
        if (!config.enabled) {
            return;
        }

        if (config.highlightEnabled) for (Map.Entry<BlockPos, List<Integer>> entry : locator.matches().entrySet()) {
            int layer = 0;
            for (int color : entry.getValue()) {
                double expansion = 0.018 + layer * 0.028;
                AABB box = new AABB(entry.getKey()).inflate(expansion);
                Gizmos.cuboid(box, GizmoStyle.stroke(color, 3.0F));
                layer++;
            }
        }

        if (!config.routeEnabled) {
            return;
        }
        int routeOffset = 0;
        for (Map.Entry<Integer, List<StorageLocator.Route>> routeGroup : locator.routesByColor().entrySet()) {
            for (StorageLocator.Route route : routeGroup.getValue()) {
                drawPath(minecraft, route.path(), routeGroup.getKey(), routeOffset * 0.035);
            }
            routeOffset++;
        }
    }

    private static void drawPath(Minecraft minecraft, List<BlockPos> path, int color, double yOffset) {
        if (path.isEmpty()) {
            return;
        }
        Vec3 previous = routePoint(minecraft, path.getFirst(), yOffset);
        for (int i = 1; i < path.size(); i++) {
            Vec3 next = routePoint(minecraft, path.get(i), yOffset);
            if (Math.abs(next.y - previous.y) > 0.01
                    && (Math.abs(next.x - previous.x) > 0.01 || Math.abs(next.z - previous.z) > 0.01)) {
                double travelY = Math.max(previous.y, next.y);
                Vec3 firstCorner = new Vec3(previous.x, travelY, previous.z);
                Vec3 secondCorner = new Vec3(next.x, travelY, next.z);
                if (previous.distanceToSqr(firstCorner) > 0.0001) {
                    Gizmos.line(previous, firstCorner, color, 2.5F);
                }
                Gizmos.line(firstCorner, secondCorner, color, 2.5F);
                if (secondCorner.distanceToSqr(next) > 0.0001) {
                    Gizmos.line(secondCorner, next, color, 2.5F);
                }
            } else {
                Gizmos.line(previous, next, color, 2.5F);
            }
            if ((i & 1) == 0) {
                Gizmos.point(next, color, 5.0F);
            }
            previous = next;
        }
        if (path.size() > 1) {
            Vec3 end = routePoint(minecraft, path.getLast(), yOffset);
            Vec3 before = routePoint(minecraft, path.get(path.size() - 2), yOffset);
            Gizmos.arrow(before, end.add(0.0, 0.25, 0.0), color, 3.0F);
        }
    }

    private static Vec3 routePoint(Minecraft minecraft, BlockPos pos, double yOffset) {
        double surface = RouteFinder.surfaceY(minecraft, pos);
        if (!Double.isFinite(surface)) {
            surface = pos.getY();
        }
        return new Vec3(pos.getX() + 0.5, surface + 0.10 + yOffset, pos.getZ() + 0.5);
    }
}

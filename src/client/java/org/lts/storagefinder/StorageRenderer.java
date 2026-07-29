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
                drawPath(route.renderPoints(), routeGroup.getKey(), routeOffset * 0.035);
            }
            routeOffset++;
        }
    }

    private static void drawPath(List<Vec3> points, int color, double yOffset) {
        if (points.isEmpty()) {
            return;
        }
        Vec3 previous = offset(points.getFirst(), yOffset);
        for (int i = 1; i < points.size(); i++) {
            Vec3 next = offset(points.get(i), yOffset);
            Gizmos.line(previous, next, color, 2.5F);
            if ((i & 1) == 0) {
                Gizmos.point(next, color, 5.0F);
            }
            previous = next;
        }
        if (points.size() > 1) {
            Vec3 end = offset(points.getLast(), yOffset);
            Vec3 before = offset(points.get(points.size() - 2), yOffset);
            Gizmos.arrow(before, end.add(0.0, 0.25, 0.0), color, 3.0F);
        }
    }

    private static Vec3 offset(Vec3 point, double yOffset) {
        return yOffset == 0.0 ? point : point.add(0.0, yOffset, 0.0);
    }
}

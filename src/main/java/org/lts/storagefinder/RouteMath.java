package org.lts.storagefinder;

public final class RouteMath {
    public static final double DIAGONAL_COST = Math.sqrt(2.0);
    public static final int MAX_FEET_RISE_PER_MOVE = 2;
    public static final int MAX_FEET_FALL_PER_MOVE = 4;

    private RouteMath() {
    }

    public static double octile(int dx, int dz) {
        int absX = Math.abs(dx);
        int absZ = Math.abs(dz);
        int diagonal = Math.min(absX, absZ);
        int straight = Math.max(absX, absZ) - diagonal;
        return diagonal * DIAGONAL_COST + straight;
    }

    public static int axisGap(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0;
    }

    public static double lowerBound(int dx, int dz, int rise, int fall) {
        double vertical = Math.max(Math.max(0, rise) / (double) MAX_FEET_RISE_PER_MOVE,
                Math.max(0, fall) / (double) MAX_FEET_FALL_PER_MOVE);
        return Math.max(octile(dx, dz), vertical);
    }

    public static double boxLowerBound(int x, int y, int z, int minX, int maxX,
                                       int minY, int maxY, int minZ, int maxZ) {
        int rise = y < minY ? minY - y : 0;
        int fall = y > maxY ? y - maxY : 0;
        return lowerBound(axisGap(x, minX, maxX), axisGap(z, minZ, maxZ), rise, fall);
    }

    public static boolean isNear(int x1, int y1, int z1, int x2, int y2, int z2,
                                 int horizontal, int vertical) {
        return Math.abs(x1 - x2) <= horizontal && Math.abs(z1 - z2) <= horizontal
                && Math.abs(y1 - y2) <= vertical;
    }
}

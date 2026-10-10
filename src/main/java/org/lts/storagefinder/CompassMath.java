package org.lts.storagefinder;

public final class CompassMath {
    private static final String[] SECTORS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private CompassMath() {
    }

    public static double bearingDegrees(double dx, double dz) {
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    public static double facingBearing(float yaw) {
        return normalize(yaw + 180.0);
    }

    public static double relativeDegrees(double bearing, float yaw) {
        return normalize(bearing - facingBearing(yaw));
    }

    public static int sector(double degrees) {
        return (int) Math.floor(normalize(degrees + 22.5) / 45.0) % 8;
    }

    public static String sectorKey(double degrees) {
        return SECTORS[sector(degrees)];
    }

    public static double normalize(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }
}

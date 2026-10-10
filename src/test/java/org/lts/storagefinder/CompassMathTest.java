package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CompassMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void bearingUsesNorthAsNegativeZ() {
        assertEquals(0.0, CompassMath.bearingDegrees(0, -10), EPSILON);
        assertEquals(90.0, CompassMath.bearingDegrees(10, 0), EPSILON);
        assertEquals(180.0, CompassMath.bearingDegrees(0, 10), EPSILON);
        assertEquals(270.0, CompassMath.bearingDegrees(-10, 0), EPSILON);
    }

    @Test
    void sectorsCoverEightDirections() {
        assertEquals("n", CompassMath.sectorKey(0.0));
        assertEquals("n", CompassMath.sectorKey(350.0));
        assertEquals("ne", CompassMath.sectorKey(45.0));
        assertEquals("e", CompassMath.sectorKey(100.0));
        assertEquals("sw", CompassMath.sectorKey(225.0));
        assertEquals("nw", CompassMath.sectorKey(330.0));
    }

    @Test
    void relativeDirectionFollowsMinecraftYaw() {
        assertEquals(0.0, CompassMath.relativeDegrees(180.0, 0.0F), EPSILON);
        assertEquals(0.0, CompassMath.relativeDegrees(0.0, 180.0F), EPSILON);
        assertEquals(90.0, CompassMath.relativeDegrees(270.0, 0.0F), EPSILON);
        assertEquals(270.0, CompassMath.relativeDegrees(90.0, -720.0F), EPSILON);
    }
}

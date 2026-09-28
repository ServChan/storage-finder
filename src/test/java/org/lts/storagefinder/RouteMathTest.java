package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RouteMathTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void octileCombinesDiagonalAndStraightSteps() {
        assertEquals(0.0, RouteMath.octile(0, 0), EPSILON);
        assertEquals(5.0, RouteMath.octile(5, 0), EPSILON);
        assertEquals(3.0 * RouteMath.DIAGONAL_COST + 2.0, RouteMath.octile(-3, 5), EPSILON);
    }

    @Test
    void axisGapIsZeroInsideTheRange() {
        assertEquals(0, RouteMath.axisGap(4, 2, 6));
        assertEquals(3, RouteMath.axisGap(-1, 2, 6));
        assertEquals(4, RouteMath.axisGap(10, 2, 6));
    }

    @Test
    void verticalGapDominatesWhenHorizontalDistanceIsSmall() {
        assertEquals(6.0, RouteMath.lowerBound(1, 0, 12, 0), EPSILON);
        assertEquals(3.0, RouteMath.lowerBound(0, 0, 0, 12), EPSILON);
        assertEquals(RouteMath.octile(20, 20), RouteMath.lowerBound(20, 20, 8, 0), EPSILON);
    }

    @Test
    void boxLowerBoundNeverExceedsTheCheapestPossibleMoveCount() {
        for (int y = -20; y <= 20; y++) {
            double bound = RouteMath.boxLowerBound(0, y, 0, 3, 3, 0, 0, 0, 0);
            int rise = Math.max(0, -y);
            int fall = Math.max(0, y);
            int minimumMoves = Math.max(3, Math.max(
                    (rise + RouteMath.MAX_FEET_RISE_PER_MOVE - 1) / RouteMath.MAX_FEET_RISE_PER_MOVE,
                    (fall + RouteMath.MAX_FEET_FALL_PER_MOVE - 1) / RouteMath.MAX_FEET_FALL_PER_MOVE));
            assertTrue(bound <= minimumMoves + EPSILON, "bound for y=" + y);
        }
    }

    @Test
    void boxLowerBoundIsConsistentAcrossSingleMoves() {
        int[][] moves = {{1, 0, 0}, {1, 2, 1}, {0, -4, 1}, {-1, 1, -1}, {0, 1, 0}};
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int[] move : moves) {
                    double here = RouteMath.boxLowerBound(x, y, 0, 2, 4, -1, 1, 0, 0);
                    double there = RouteMath.boxLowerBound(x + move[0], y + move[1], move[2],
                            2, 4, -1, 1, 0, 0);
                    double cost = move[0] != 0 && move[2] != 0 ? RouteMath.DIAGONAL_COST : 1.0;
                    assertTrue(here <= cost + there + EPSILON,
                            "inconsistent at x=" + x + " y=" + y);
                }
            }
        }
    }

    @Test
    void nearnessUsesSeparateHorizontalAndVerticalTolerances() {
        assertTrue(RouteMath.isNear(0, 64, 0, 4, 66, -4, 4, 2));
        assertFalse(RouteMath.isNear(0, 64, 0, 5, 64, 0, 4, 2));
        assertFalse(RouteMath.isNear(0, 64, 0, 0, 67, 0, 4, 2));
    }
}

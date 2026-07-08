package net.astronomy.multilib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Pure-math invariants of {@link RotationUtils#rotate}. These encode the properties the matchers
 * rely on (proper rotations: composable, length-preserving, axis-coordinate-invariant) rather than
 * one specific handedness table, so a legitimate re-derivation of the tables stays green while an
 * accidental reflection or axis swap fails.
 */
class RotationUtilsTest {

    private static final int[][] SAMPLE_VECTORS = {
            {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 2, 3}, {-2, 5, -7}, {0, 0, 0}
    };
    private static final String[] AXES = {"X", "Y", "Z"};

    @Test
    void zeroAndFullTurnAreIdentity() {
        for (String axis : AXES) {
            for (int[] v : SAMPLE_VECTORS) {
                assertArrayEquals(v, RotationUtils.rotate(v[0], v[1], v[2], axis, 0));
                assertArrayEquals(v, RotationUtils.rotate(v[0], v[1], v[2], axis, 360));
            }
        }
    }

    @Test
    void fourQuarterTurnsComposeToIdentity() {
        for (String axis : AXES) {
            for (int[] v : SAMPLE_VECTORS) {
                int[] r = v;
                for (int i = 0; i < 4; i++) {
                    r = RotationUtils.rotate(r[0], r[1], r[2], axis, 90);
                }
                assertArrayEquals(v, r, "4x90 around " + axis);
            }
        }
    }

    @Test
    void quarterPlusThreeQuartersIsIdentity() {
        for (String axis : AXES) {
            for (int[] v : SAMPLE_VECTORS) {
                int[] r = RotationUtils.rotate(v[0], v[1], v[2], axis, 90);
                r = RotationUtils.rotate(r[0], r[1], r[2], axis, 270);
                assertArrayEquals(v, r, "90+270 around " + axis);
            }
        }
    }

    @Test
    void negativeAngleEqualsComplement() {
        for (String axis : AXES) {
            for (int[] v : SAMPLE_VECTORS) {
                assertArrayEquals(
                        RotationUtils.rotate(v[0], v[1], v[2], axis, 270),
                        RotationUtils.rotate(v[0], v[1], v[2], axis, -90),
                        "-90 == 270 around " + axis);
            }
        }
    }

    @Test
    void rotationAxisCoordinateIsInvariant() {
        int[] v = {1, 2, 3};
        for (int angle : new int[]{90, 180, 270}) {
            assertEqualsAxis(v[0], RotationUtils.rotate(v[0], v[1], v[2], "X", angle)[0], "X", angle);
            assertEqualsAxis(v[1], RotationUtils.rotate(v[0], v[1], v[2], "Y", angle)[1], "Y", angle);
            assertEqualsAxis(v[2], RotationUtils.rotate(v[0], v[1], v[2], "Z", angle)[2], "Z", angle);
        }
    }

    private static void assertEqualsAxis(int expected, int actual, String axis, int angle) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                axis + "-coordinate must survive rotation around " + axis + " by " + angle);
    }

    @Test
    void lengthIsPreserved() {
        int[] v = {1, 2, 3};
        int lenSq = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        for (String axis : AXES) {
            for (int angle : new int[]{90, 180, 270}) {
                int[] r = RotationUtils.rotate(v[0], v[1], v[2], axis, angle);
                org.junit.jupiter.api.Assertions.assertEquals(lenSq,
                        r[0] * r[0] + r[1] * r[1] + r[2] * r[2],
                        "length around " + axis + " by " + angle);
            }
        }
    }

    @Test
    void lowercaseAxisIsAccepted() {
        assertArrayEquals(
                RotationUtils.rotate(1, 2, 3, "Y", 90),
                RotationUtils.rotate(1, 2, 3, "y", 90));
    }

    @Test
    void unknownAxisPassesThroughUnchanged() {
        assertArrayEquals(new int[]{1, 2, 3}, RotationUtils.rotate(1, 2, 3, "W", 90));
    }
}

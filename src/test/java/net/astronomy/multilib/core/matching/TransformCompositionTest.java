package net.astronomy.multilib.core.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ShapedMatcher#applyTransform}'s tip+spin composition - the contract every orientation the
 * matchers try is built on: the pattern's layer-stacking axis (+Y in pattern space) must land exactly
 * on the target axis (positive for X/Z, negative for the _FLIP forms), and the spin must never move
 * it off that axis again.
 */
class TransformCompositionTest {

    private static final int[] LAYER_AXIS = {0, 1, 0}; // +Y in pattern-local space

    @Test
    void yAxisRotationZeroIsIdentity() {
        assertArrayEquals(new int[]{1, 2, 3}, ShapedMatcher.applyTransform(1, 2, 3, "Y", 0));
    }

    @Test
    void tipLandsLayerAxisOnTargetAxis() {
        assertArrayEquals(new int[]{1, 0, 0}, tip("X"), "X tips +Y onto +X");
        assertArrayEquals(new int[]{-1, 0, 0}, tip("X_FLIP"), "X_FLIP tips +Y onto -X");
        assertArrayEquals(new int[]{0, 0, 1}, tip("Z"), "Z tips +Y onto +Z");
        assertArrayEquals(new int[]{0, 0, -1}, tip("Z_FLIP"), "Z_FLIP tips +Y onto -Z");
    }

    private static int[] tip(String axis) {
        return ShapedMatcher.applyTransform(LAYER_AXIS[0], LAYER_AXIS[1], LAYER_AXIS[2], axis, 0);
    }

    @Test
    void spinPreservesTargetAxisCoordinate() {
        // Once tipped, spinning around the target axis must keep the layer axis pinned there -
        // otherwise a structure's vertical extent would wander as rotations are tried.
        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(1, ShapedMatcher.applyTransform(0, 1, 0, "X", rotation)[0],
                    "X spin " + rotation);
            assertEquals(-1, ShapedMatcher.applyTransform(0, 1, 0, "X_FLIP", rotation)[0],
                    "X_FLIP spin " + rotation);
            assertEquals(1, ShapedMatcher.applyTransform(0, 1, 0, "Z", rotation)[2],
                    "Z spin " + rotation);
            assertEquals(-1, ShapedMatcher.applyTransform(0, 1, 0, "Z_FLIP", rotation)[2],
                    "Z_FLIP spin " + rotation);
        }
    }

    @Test
    void fourSpinsComposeBackToPureTip() {
        // Spinning 4x90 around the target axis is the identity spin: rotation index 0..3 at index 0
        // and a manual 4th quarter-turn must agree for every axis form.
        int[] v = {1, 2, 3};
        for (String axis : new String[]{"X", "X_FLIP", "Z", "Z_FLIP", "Y"}) {
            int[] spun0 = ShapedMatcher.applyTransform(v[0], v[1], v[2], axis, 0);
            int[] spun3 = ShapedMatcher.applyTransform(v[0], v[1], v[2], axis, 3);
            String rotAxis = axis.startsWith("X") ? "X" : axis.startsWith("Z") ? "Z" : "Y";
            int[] backAround = net.astronomy.multilib.util.RotationUtils.rotate(
                    spun3[0], spun3[1], spun3[2], rotAxis, 90);
            assertArrayEquals(spun0, backAround, axis + ": spin3 + one more quarter == spin0");
        }
    }

    @Test
    void lengthIsPreservedAcrossEveryTransform() {
        int[] v = {2, 3, 5};
        int lenSq = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        for (String axis : new String[]{"Y", "X", "X_FLIP", "Z", "Z_FLIP"}) {
            for (int rotation = 0; rotation < 4; rotation++) {
                int[] r = ShapedMatcher.applyTransform(v[0], v[1], v[2], axis, rotation);
                assertEquals(lenSq, r[0] * r[0] + r[1] * r[1] + r[2] * r[2],
                        axis + "/" + rotation);
            }
        }
    }
}

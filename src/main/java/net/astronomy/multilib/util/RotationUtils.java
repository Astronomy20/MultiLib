package net.astronomy.multilib.util;

/**
 * Utility for rotating 3D coordinates around all three axes (X, Y, Z).
 * Supports 0°, 90°, 180°, and 270° rotations.
 *
 * Designed for Minecraft NeoForge 21.1.213.
 */
public class RotationUtils {

    /**
     * Rotates a coordinate around the specified axis by a given angle (in degrees).
     *
     * @param x The X coordinate
     * @param y The Y coordinate
     * @param z The Z coordinate
     * @param axis The axis to rotate around ("X", "Y", "Z")
     * @param angle The rotation angle in degrees (only multiples of 90 are supported)
     * @return The rotated coordinates as {x, y, z}
     */
    public static int[] rotate(int x, int y, int z, String axis, int angle) {
        int normalized = ((angle % 360) + 360) % 360;
        normalized = (normalized / 90) % 4; // convert to 0–3 (representing 0°, 90°, 180°, 270°)

        // Matching both cases directly avoids the toUpperCase() allocation on this hot path
        // (called once per pattern cell per orientation attempt); accepted inputs are unchanged.
        return switch (axis) {
            case "X", "x" -> rotateAroundX(x, y, z, normalized);
            case "Y", "y" -> rotateAroundY(x, y, z, normalized);
            case "Z", "z" -> rotateAroundZ(x, y, z, normalized);
            default -> new int[]{x, y, z};
        };
    }

    /** Rotate around the X-axis in 90° steps. */
    private static int[] rotateAroundX(int x, int y, int z, int step) {
        return switch (step) {
            case 1 -> new int[]{x, -z, y};     // 90°
            case 2 -> new int[]{x, -y, -z};    // 180°
            case 3 -> new int[]{x, z, -y};     // 270°
            default -> new int[]{x, y, z};     // 0°
        };
    }

    /** Rotate around the Y-axis in 90° steps. */
    private static int[] rotateAroundY(int x, int y, int z, int step) {
        return switch (step) {
            case 1 -> new int[]{-z, y, x};     // 90°
            case 2 -> new int[]{-x, y, -z};    // 180°
            case 3 -> new int[]{z, y, -x};     // 270°
            default -> new int[]{x, y, z};     // 0°
        };
    }

    /** Rotate around the Z-axis in 90° steps. */
    private static int[] rotateAroundZ(int x, int y, int z, int step) {
        return switch (step) {
            case 1 -> new int[]{y, -x, z};     // 90°
            case 2 -> new int[]{-x, -y, z};    // 180°
            case 3 -> new int[]{-y, x, z};     // 270°
            default -> new int[]{x, y, z};     // 0°
        };
    }
}
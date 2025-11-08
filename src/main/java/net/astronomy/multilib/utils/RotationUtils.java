package net.astronomy.multilib.utils;

/**
 * Utility for rotating and mirroring coordinates around different axes.
 *
 * Supports:
 *  - Horizontal rotation (Y-axis)
 *  - Vertical rotation (X/Z-axis)
 *  - Mirroring (X/Z)
 */
public class RotationUtils {

    public static int[] transform(int x, int y, int z, int rotation, boolean vertical, String axis) {
        int newX = x;
        int newY = y;
        int newZ = z;

        if (vertical) {
            int[] v = rotateVertical(x, y, z, axis, rotation);
            newX = v[0];
            newY = v[1];
            newZ = v[2];
        } else {
            int[] r = rotate(x, z, rotation);
            newX = r[0];
            newZ = r[1];
        }

        return new int[]{newX, newY, newZ};
    }

    /**
     * Rotates a coordinate around the Y axis (horizontal rotation).
     * @param x X offset
     * @param z Z offset
     * @param rotation number of 90° clockwise turns (0–3)
     * @return rotated {x, z}
     */
    public static int[] rotate(int x, int z, int rotation) {
        return switch (rotation) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /**
     * Rotates a 3D coordinate vertically around the X or Z axis.
     * Useful for allowing "standing" or "lying" pattern orientations.
     *
     * @param x X offset
     * @param y Y offset
     * @param z Z offset
     * @param axis "X" (rotate around X axis) or "Z" (rotate around Z axis)
     * @param rotation number of 90° clockwise turns (0–3)
     * @return rotated {x, y, z}
     */
    public static int[] rotateVertical(int x, int y, int z, String axis, int rotation) {
        int newX = x;
        int newY = y;
        int newZ = z;

        switch (axis.toUpperCase()) {
            case "X" -> {
                rotation = rotation % 4;
                switch (rotation) {
                    case 1 -> { int t = newY; newY = -newZ; newZ = t; }
                    case 2 -> { newY = -newY; newZ = -newZ; }
                    case 3 -> { int t = newY; newY = newZ; newZ = -t; }
                }
            }
            case "Z" -> {
                rotation = rotation % 4;
                switch (rotation) {
                    case 1 -> { int t = newY; newY = newX; newX = -t; }
                    case 2 -> { newY = -newY; newX = -newX; }
                    case 3 -> { int t = newY; newY = -newX; newX = t; }
                }
            }
        }

        return new int[]{newX, newY, newZ};
    }
}
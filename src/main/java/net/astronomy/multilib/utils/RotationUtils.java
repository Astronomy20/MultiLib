package net.astronomy.multilib.utils;

public class RotationUtils {
    public static int[] rotate(int x, int z, int rotation) {
        return switch (rotation) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    public static int[] mirror(int x, int z, String axis) {
        return switch (axis.toUpperCase()) {
            case "X" -> new int[]{x, -z};
            case "Z" -> new int[]{-x, z};
            case "XZ", "ZX" -> new int[]{-x, -z};
            default -> new int[]{x, z};
        };
    }
}
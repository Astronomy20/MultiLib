package net.astronomy.multilib.api.definition;

/**
 * A single allowed rotation step (90, 180, or 270/-90 degrees) around a given world axis, declared via
 * {@code MultiblockBuilder.allowRotation(axis, angles...)}. More granular than {@link RotationMode}:
 * lets a dev allow e.g. only a 180-degree flip around Y, or a 90-degree tilt around X, instead of the
 * coarse NONE/HORIZONTAL/ALL choice. The unrotated (identity) orientation is always tried regardless.
 */
public record AllowedRotation(RotationAxis axis, int angle) {
    public AllowedRotation {
        int normalized = ((angle % 360) + 360) % 360;
        if (normalized != 90 && normalized != 180 && normalized != 270) {
            throw new IllegalArgumentException("angle must be 90, 180, 270 or -90, was: " + angle);
        }
    }

    /** The angle normalized to the [90, 270] range (e.g. -90 becomes 270). */
    public int normalizedAngle() {
        return ((angle % 360) + 360) % 360;
    }
}

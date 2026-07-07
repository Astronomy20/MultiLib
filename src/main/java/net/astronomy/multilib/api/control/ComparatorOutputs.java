package net.astronomy.multilib.api.control;

/**
 * Static helpers turning a few common "how full is this" quantities into a vanilla 0-15 comparator
 * signal, so devs stop re-deriving the same scaling math (and its off-by-one edge cases) for every new
 * energy/fluid/generic storage they wire a comparator to.
 * <p>
 * All of these mirror vanilla's own item-container convention rather than a plain linear scale: see
 * {@code AbstractContainerMenu.getRedstoneSignalFromContainer} - a completely empty container reads 0,
 * but <em>any</em> non-empty container reads at least 1, with 15 only once completely full. That
 * "0 only when truly empty, else at least 1" rule is what stops a container (or energy buffer, or fluid
 * tank) that's merely "almost empty" from reading identically to one that's completely empty, which a
 * naive {@code floor(fraction * 15)} would do.
 */
public final class ComparatorOutputs {

    private ComparatorOutputs() {}

    /**
     * Scales a 0.0-1.0 fraction (clamped) to a 0-15 comparator level, using the same "0 only when
     * empty, else at least 1" convention as the other methods on this class.
     */
    public static int fromFraction(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        if (clamped <= 0.0) return 0;
        return (int) Math.min(15, 1 + Math.floor(clamped * 14.0));
    }

    /** Scales stored/capacity energy (e.g. an energy storage handler) to a 0-15 comparator level. */
    public static int fromStoredEnergy(long stored, long capacity) {
        return scaleWithMinimum(stored, capacity);
    }

    /** Scales a fluid tank's current amount/capacity to a 0-15 comparator level. */
    public static int fromFluid(int amount, int capacity) {
        return scaleWithMinimum(amount, capacity);
    }

    /** Generic value/max scaling to a 0-15 comparator level, for any other "how full" quantity. */
    public static int scaled(int value, int max) {
        return scaleWithMinimum(value, max);
    }

    private static int scaleWithMinimum(long value, long max) {
        if (max <= 0 || value <= 0) return 0;
        long clamped = Math.min(value, max);
        long level = 1 + (clamped * 14) / max;
        return (int) Math.min(15, level);
    }
}

package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Solid of revolution: sweeps a {@code crossSection} pattern 360 degrees around the vertical (Y) axis,
 * producing donut/torus-like shapes with an arbitrary tube profile instead of a fixed circle.
 *
 * <p>The cross section is itself a {@link PatternProvider}, queried as {@code (localRadius, y, 0)}, where
 * {@code localRadius} is the distance from the central axis minus {@code minRadius} — i.e. the section's
 * own X axis maps to "how far into the tube band", and Y maps directly to height. {@code minRadius} is the
 * band's inner cutoff and also the anchor used to index into the section; {@code maxRadius} is the outer
 * cutoff, used only to bound the sweep and the resulting bounding box. A plain circular donut (equivalent
 * to {@link TorusProvider}) can be built by passing a small circle test as the cross section.
 *
 * <p>Centred horizontally on {@code (maxRadius, maxRadius)}; bounding box is
 * {@code (2*maxRadius+1, height, 2*maxRadius+1)}.
 */
public class RevolutionProvider implements PatternProvider {
    private final int minRadius;
    private final int maxRadius;
    private final int height;
    private final PatternProvider crossSection;
    private final int centerXZ;
    private final Vec3i size;

    public RevolutionProvider(int minRadius, int maxRadius, int height, PatternProvider crossSection) {
        if (minRadius < 0 || minRadius > maxRadius) {
            throw new IllegalArgumentException(
                "minRadius must be in [0, maxRadius], got " + minRadius + " with maxRadius " + maxRadius);
        }
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.height = height;
        this.crossSection = crossSection;
        this.centerXZ = maxRadius;
        int d = 2 * maxRadius + 1;
        this.size = new Vec3i(d, height, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= height) return null;
        int cx = x - centerXZ;
        int cz = z - centerXZ;
        double r = Math.sqrt((double) cx * cx + (double) cz * cz);
        if (r > maxRadius + 0.5) return null;
        int localRadius = (int) Math.round(r) - minRadius;
        if (localRadius < 0) return null;
        return crossSection.getIngredientAt(localRadius, y, 0);
    }

    public int getMinRadius() { return minRadius; }
    public int getMaxRadius() { return maxRadius; }
    public int getHeight() { return height; }
    public PatternProvider getCrossSection() { return crossSection; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

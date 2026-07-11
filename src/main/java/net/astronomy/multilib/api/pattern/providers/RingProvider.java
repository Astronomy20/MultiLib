package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Vertical annulus (ring / hollow cylinder wall). Fills the band between {@code innerRadius} and
 * {@code outerRadius} in the XZ plane, extruded over {@code height} layers. With {@code height == 1}
 * this is a flat ring; with a larger height it is a tube. Set {@code innerRadius == 0} to get a
 * solid disk column (equivalent to {@link CylinderProvider}).
 *
 * <p>Centred horizontally on {@code (outerRadius, outerRadius)}; bounding box is
 * {@code (2*outerRadius+1, height, 2*outerRadius+1)}.
 */
public class RingProvider implements PatternProvider {
    private final int outerRadius;
    private final int innerRadius;
    private final int height;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public RingProvider(int outerRadius, int innerRadius, int height, BlockIngredient ingredient) {
        if (innerRadius < 0 || innerRadius > outerRadius) {
            throw new IllegalArgumentException(
                "innerRadius must be in [0, outerRadius], got " + innerRadius + " with outerRadius " + outerRadius);
        }
        this.outerRadius = outerRadius;
        this.innerRadius = innerRadius;
        this.height = height;
        this.ingredient = ingredient;
        int d = 2 * outerRadius + 1;
        this.size = new Vec3i(d, height, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= height) return null;
        int cx = x - outerRadius;
        int cz = z - outerRadius;
        int dist2 = cx * cx + cz * cz;
        return (dist2 >= innerRadius * innerRadius && dist2 <= outerRadius * outerRadius) ? ingredient : null;
    }

    public int getOuterRadius() { return outerRadius; }
    public int getInnerRadius() { return innerRadius; }
    public int getHeight() { return height; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

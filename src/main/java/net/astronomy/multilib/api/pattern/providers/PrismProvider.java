package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Solid regular-polygon prism: a filled N-gon (of the given circumradius) extruded over
 * {@code height} layers. {@code sides == 4} gives a square column, {@code 6} a hexagonal one, and
 * so on. As {@code sides} grows the footprint approaches {@link CylinderProvider}.
 *
 * <p>One vertex points toward +X. Centred horizontally on {@code (radius, radius)}; bounding box is
 * {@code (2*radius+1, height, 2*radius+1)}.
 */
public class PrismProvider implements PatternProvider {
    private final int sides;
    private final int radius;
    private final int height;
    private final BlockIngredient ingredient;
    private final double sector;
    private final double apothemRatio;
    private final Vec3i size;

    public PrismProvider(int sides, int radius, int height, BlockIngredient ingredient) {
        if (sides < 3) {
            throw new IllegalArgumentException("A prism needs at least 3 sides, got " + sides);
        }
        this.sides = sides;
        this.radius = radius;
        this.height = height;
        this.ingredient = ingredient;
        this.sector = 2.0 * Math.PI / sides;
        this.apothemRatio = Math.cos(Math.PI / sides);
        int d = 2 * radius + 1;
        this.size = new Vec3i(d, height, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= height) return null;
        int cx = x - radius;
        int cz = z - radius;
        double d = Math.sqrt((double) cx * cx + (double) cz * cz);
        if (d <= 0.5) return ingredient; // centre cell
        // Distance from centre to the polygon edge along this ray (vertices sit at `radius`).
        double angle = Math.atan2(cz, cx);
        double a = angle % sector;
        if (a < 0) a += sector;
        double edge = radius * apothemRatio / Math.cos(a - sector / 2.0);
        return (d <= edge) ? ingredient : null;
    }

    public int getSides() { return sides; }
    public int getRadius() { return radius; }
    public int getHeight() { return height; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

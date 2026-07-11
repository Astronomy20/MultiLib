package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Solid cone with a circular base of {@code baseRadius} at {@code y == 0} that tapers
 * to a point at {@code y == height - 1}. Each layer is a filled disk whose radius shrinks
 * linearly with height.
 *
 * <p>The horizontal footprint is centred on {@code (baseRadius, baseRadius)}; the bounding
 * box is {@code (2*baseRadius+1, height, 2*baseRadius+1)}.
 */
public class ConeProvider implements PatternProvider {
    private final int baseRadius;
    private final int height;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public ConeProvider(int baseRadius, int height, BlockIngredient ingredient) {
        this.baseRadius = baseRadius;
        this.height = height;
        this.ingredient = ingredient;
        int d = 2 * baseRadius + 1;
        this.size = new Vec3i(d, height, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= height) return null;
        // radius at this layer: full at the base, zero at the apex.
        double t = height <= 1 ? 0.0 : (double) (height - 1 - y) / (height - 1);
        double r = baseRadius * t;
        int cx = x - baseRadius;
        int cz = z - baseRadius;
        return (cx * cx + cz * cz <= r * r) ? ingredient : null;
    }

    public int getBaseRadius() { return baseRadius; }
    public int getHeight() { return height; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

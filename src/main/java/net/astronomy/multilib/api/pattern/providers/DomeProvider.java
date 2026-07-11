package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Solid hemisphere (dome) of the given radius. The flat face sits on {@code y == 0} and the
 * shell curves up to the pole at {@code y == radius}. Equivalent to the upper half of a
 * {@link SphereProvider}.
 *
 * <p>Centred horizontally on {@code (radius, radius)}; bounding box is
 * {@code (2*radius+1, radius+1, 2*radius+1)}. For a shell-only dome use {@link HollowDomeProvider}.
 */
public class DomeProvider implements PatternProvider {
    private final int radius;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public DomeProvider(int radius, BlockIngredient ingredient) {
        this.radius = radius;
        this.ingredient = ingredient;
        int d = 2 * radius + 1;
        this.size = new Vec3i(d, radius + 1, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y > radius) return null;
        int cx = x - radius;
        int cz = z - radius;
        return (cx * cx + y * y + cz * cz <= radius * radius) ? ingredient : null;
    }

    public int getRadius() { return radius; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

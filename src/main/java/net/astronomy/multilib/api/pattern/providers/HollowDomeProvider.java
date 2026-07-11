package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Shell-only hemisphere (dome), i.e. the curved surface of a {@link DomeProvider} without its
 * solid interior. The flat face sits on {@code y == 0} and the shell curves up to the pole at
 * {@code y == radius}. Uses the same one-voxel-thick shell test as {@link HollowSphereProvider}.
 *
 * <p>Centred horizontally on {@code (radius, radius)}; bounding box is
 * {@code (2*radius+1, radius+1, 2*radius+1)}. This provider does not add a floor — pair it with a
 * {@link CompositeProvider} union if you need a closed base.
 */
public class HollowDomeProvider implements PatternProvider {
    private final int radius;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public HollowDomeProvider(int radius, BlockIngredient ingredient) {
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
        int dist2 = cx * cx + y * y + cz * cz;
        int r2 = radius * radius;
        return (dist2 >= r2 - 1 && dist2 <= r2) ? ingredient : null;
    }

    public int getRadius() { return radius; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

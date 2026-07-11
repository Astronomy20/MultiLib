package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

/**
 * Solid torus (donut) lying flat in the XZ plane. {@code majorRadius} is the distance from the
 * centre of the hole to the centre of the tube; {@code minorRadius} is the tube's own radius.
 *
 * <p>A point is inside when {@code (sqrt(cx^2 + cz^2) - majorRadius)^2 + cy^2 <= minorRadius^2}.
 * Centred on {@code (majorRadius+minorRadius, minorRadius, majorRadius+minorRadius)}; bounding box
 * is {@code (2*(majorRadius+minorRadius)+1, 2*minorRadius+1, 2*(majorRadius+minorRadius)+1)}.
 */
public class TorusProvider implements PatternProvider {
    private final int majorRadius;
    private final int minorRadius;
    private final BlockIngredient ingredient;
    private final int centerXZ;
    private final Vec3i size;

    public TorusProvider(int majorRadius, int minorRadius, BlockIngredient ingredient) {
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
        this.ingredient = ingredient;
        this.centerXZ = majorRadius + minorRadius;
        int d = 2 * (majorRadius + minorRadius) + 1;
        this.size = new Vec3i(d, 2 * minorRadius + 1, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        int cx = x - centerXZ;
        int cy = y - minorRadius;
        int cz = z - centerXZ;
        double q = Math.sqrt((double) cx * cx + (double) cz * cz) - majorRadius;
        return (q * q + (double) cy * cy <= (double) minorRadius * minorRadius) ? ingredient : null;
    }

    public int getMajorRadius() { return majorRadius; }
    public int getMinorRadius() { return minorRadius; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

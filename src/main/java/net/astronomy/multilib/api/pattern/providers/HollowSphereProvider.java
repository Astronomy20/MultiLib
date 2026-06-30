package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class HollowSphereProvider implements PatternProvider {
    private final int radius;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public HollowSphereProvider(int radius, BlockIngredient ingredient) {
        this.radius = radius;
        this.ingredient = ingredient;
        int d = 2 * radius + 1;
        this.size = new Vec3i(d, d, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        int cx = x - radius;
        int cy = y - radius;
        int cz = z - radius;
        int dist2 = cx * cx + cy * cy + cz * cz;
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

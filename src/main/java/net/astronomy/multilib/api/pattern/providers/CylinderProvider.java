package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class CylinderProvider implements PatternProvider {
    private final int radius;
    private final int height;
    private final BlockIngredient ingredient;
    private final Vec3i size;

    public CylinderProvider(int radius, int height, BlockIngredient ingredient) {
        this.radius = radius;
        this.height = height;
        this.ingredient = ingredient;
        int d = 2 * radius + 1;
        this.size = new Vec3i(d, height, d);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= height) return null;
        int cx = x - radius;
        int cz = z - radius;
        return (cx * cx + cz * cz <= radius * radius) ? ingredient : null;
    }

    public int getRadius() { return radius; }
    public int getHeight() { return height; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

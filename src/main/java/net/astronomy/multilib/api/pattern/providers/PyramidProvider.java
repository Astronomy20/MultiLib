package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class PyramidProvider implements PatternProvider {
    private final int baseSize;
    private final BlockIngredient ingredient;

    public PyramidProvider(int baseSize, BlockIngredient ingredient) {
        this.baseSize = baseSize;
        this.ingredient = ingredient;
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= baseSize) return null;
        int layerWidth = baseSize - y;
        int xMin = (baseSize - layerWidth) / 2;
        int xMax = xMin + layerWidth - 1;
        int zMin = (baseSize - layerWidth) / 2;
        int zMax = zMin + layerWidth - 1;
        return (x >= xMin && x <= xMax && z >= zMin && z <= zMax) ? ingredient : null;
    }

    public int getBaseSize() { return baseSize; }
    public BlockIngredient getIngredient() { return ingredient; }

    @Override
    public Vec3i getSize() {
        return new Vec3i(baseSize, baseSize, baseSize);
    }
}

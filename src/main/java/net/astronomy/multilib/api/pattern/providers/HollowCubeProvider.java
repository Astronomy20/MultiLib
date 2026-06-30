package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

public class HollowCubeProvider implements PatternProvider {
    private final int width;
    private final int height;
    private final int depth;
    private final BlockIngredient shell;
    private final @Nullable BlockIngredient interior;
    private final Vec3i size;

    public HollowCubeProvider(int width, int height, int depth,
                               BlockIngredient shell, @Nullable BlockIngredient interior) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.shell = shell;
        this.interior = interior;
        this.size = new Vec3i(width, height, depth);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        boolean onShell = x == 0 || x == width - 1
                || y == 0 || y == height - 1
                || z == 0 || z == depth - 1;
        return onShell ? shell : interior;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
    public BlockIngredient getShell() { return shell; }
    public @Nullable BlockIngredient getInterior() { return interior; }

    @Override
    public Vec3i getSize() {
        return size;
    }
}

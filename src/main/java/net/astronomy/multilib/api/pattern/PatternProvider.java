package net.astronomy.multilib.api.pattern;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface PatternProvider {
    @Nullable BlockIngredient getIngredientAt(int x, int y, int z);

    default Vec3i getSize() { return new Vec3i(1, 1, 1); }
}

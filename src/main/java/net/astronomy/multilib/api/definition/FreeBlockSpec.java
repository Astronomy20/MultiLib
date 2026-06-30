package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record FreeBlockSpec(
        BlockIngredient ingredient,
        int min,
        int max,
        @Nullable List<BlockPos> allowedPositions
) {}

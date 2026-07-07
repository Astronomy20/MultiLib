package net.astronomy.multilib.api.progress;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A placed block that doesn't satisfy the pattern at its position - as opposed to a
 * {@link MissingBlock} (nothing placed there at all). {@code wrongState} tells apart "right block,
 * wrong blockstate property" (e.g. facing) from "an entirely different block", mirroring the ghost
 * overlay's {@code WRONG}/{@code WRONG_STATE} distinction - see {@link BlockIngredient#matchesBlockType}.
 */
public record StructureMismatch(BlockPos pos, char symbol, BlockIngredient expected, BlockState actual,
                                 boolean wrongState) {
}

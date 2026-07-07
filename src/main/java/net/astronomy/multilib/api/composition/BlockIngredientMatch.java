package net.astronomy.multilib.api.composition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The raw match for a single position of an already-formed structure - which symbol occupies
 * {@code pos} in the definition's pattern, and what block is actually sitting there in the world.
 * This is the per-position granularity {@link CompositionResult} aggregates on top of.
 */
public record BlockIngredientMatch(BlockPos pos, char symbol, BlockState actualState) {}

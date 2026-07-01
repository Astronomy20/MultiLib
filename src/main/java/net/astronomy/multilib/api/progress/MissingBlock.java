package net.astronomy.multilib.api.progress;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A single pattern position that isn't correctly filled yet — either empty, or occupied by a block
 * that doesn't match what the pattern expects there.
 */
public record MissingBlock(BlockPos pos, BlockState expectedState) {}

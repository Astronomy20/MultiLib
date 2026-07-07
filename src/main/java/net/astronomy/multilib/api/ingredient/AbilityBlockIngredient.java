package net.astronomy.multilib.api.ingredient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.Set;

/**
 * Matches any block whose block entity exposes {@link #capability} at match-time, rather than a
 * statically declared block/tag - see {@link BlockIngredient#ability}. This lets third-party addons
 * interoperate without MultiLib (or the addon) having to pre-tag every compatible block.
 */
class AbilityBlockIngredient implements BlockIngredient {
    private final BlockCapability<?, Direction> capability;
    private final Block previewBlock;

    AbilityBlockIngredient(BlockCapability<?, Direction> capability, Block previewBlock) {
        this.capability = capability;
        this.previewBlock = previewBlock;
    }

    @Override
    public boolean matches(BlockState state) {
        // No context (level/pos) available here - a capability can't be queried without a Level.
        // Only the context-aware overload below can actually match.
        return false;
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getCapability(capability, pos, (Direction) null) != null) return true;
        for (Direction direction : Direction.values()) {
            if (level.getCapability(capability, pos, direction) != null) return true;
        }
        return false;
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        // Only used for previews (JEI/REI/EMI, ghost overlay) - the real match is dynamic and can't be
        // enumerated ahead of time.
        return Set.of(previewBlock);
    }

    @Override
    public BlockState getRenderState() {
        return previewBlock.defaultBlockState();
    }
}

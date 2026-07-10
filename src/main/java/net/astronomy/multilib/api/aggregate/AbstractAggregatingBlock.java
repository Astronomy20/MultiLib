package net.astronomy.multilib.api.aggregate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Optional convenience base for a neighbor-aggregating block: wires {@link AggregationEngine}'s
 * recompute calls into the three vanilla hooks that actually fire on topology changes
 * ({@code onPlace}, {@code onRemove}, {@code neighborChanged}). Purely plumbing - it declares no shape,
 * no capacity, no content of any kind.
 * <p>
 * Not required: a block that can't extend this (it already extends something else) can call
 * {@link AggregationEngine#onPlaced}/{@link AggregationEngine#onRemoved}/
 * {@link AggregationEngine#onNeighborChanged} directly from its own overrides of those same three hooks
 * instead - that's the entire contract this class fulfills.
 */
public abstract class AbstractAggregatingBlock extends Block implements EntityBlock {

    protected AbstractAggregatingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        AggregationEngine.onPlaced(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            AggregationEngine.onRemoved(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
        AggregationEngine.onNeighborChanged(level, pos);
    }
}

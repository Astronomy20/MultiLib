package net.astronomy.multilib.api.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Base class for blocks that can take part in a {@code .model(...)} multiblock: when the structure
 * they're in is formed and has an associated Master-Dummy model, every part becomes invisible
 * (collision/interaction untouched) except the core, which renders the structure's model instead via
 * {@link MultiblockMasterModelRenderer}. Toggled through the {@link #MODEL_HIDDEN} blockstate
 * property rather than swapping the block, so the block entity and its data are never disturbed.
 */
public abstract class AbstractMultiblockPartBlock extends Block {
    public static final BooleanProperty MODEL_HIDDEN = BooleanProperty.create("multilib_model_hidden");

    protected AbstractMultiblockPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(MODEL_HIDDEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODEL_HIDDEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(MODEL_HIDDEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /**
     * Flips {@link #MODEL_HIDDEN} on the block at {@code pos} without disturbing its block entity,
     * if and only if the block there is one of ours and the property isn't already at that value.
     * {@code UPDATE_KNOWN_SHAPE} skips the neighbor shape re-check since this property never affects
     * collision shape.
     */
    public static void setModelHidden(Level level, BlockPos pos, boolean hidden) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractMultiblockPartBlock) || state.getValue(MODEL_HIDDEN) == hidden) return;
        level.setBlock(pos, state.setValue(MODEL_HIDDEN, hidden),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}

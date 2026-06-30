package net.astronomy.multilib.example;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.callback.MultiblockFormedContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Test/demo controller BlockEntity for the multilib:example structure (see ExampleSetup).
 */
public class ExampleControllerBE extends AbstractMultiblockControllerBE {

    public ExampleControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        setValidationInterval(100); // re-validates every 5 seconds
    }

    // Referenced by ExampleSetup's BlockEntityType.Builder — kept here, instead of inline in
    // ExampleSetup's own field initializer, so the lazy lookup of CONTROLLER_BE_TYPE isn't a
    // self-reference inside ExampleSetup's initializer.
    public static ExampleControllerBE create(BlockPos pos, BlockState state) {
        return new ExampleControllerBE(ExampleSetup.CONTROLLER_BE_TYPE, pos, state);
    }

    @Override
    protected void onFormed(MultiblockFormedContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Example structure formed at {}", ctx.instance().getOrigin());
        // Part-block hiding is handled automatically by the framework (definition has .model()).
        // Only the core's own visual needs to be toggled here.
        BlockState myState = getBlockState();
        if (myState.hasProperty(ExampleControllerBlock.FORMED)) {
            ctx.level().setBlock(worldPosition, myState.setValue(ExampleControllerBlock.FORMED, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    protected void onBroken(MultiblockBrokenContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Example structure broken, removed pos: {}", ctx.removedPos());
        // Part-block un-hiding is handled automatically by the framework.
        BlockState myState = getBlockState();
        if (myState.hasProperty(ExampleControllerBlock.FORMED)) {
            ctx.level().setBlock(worldPosition, myState.setValue(ExampleControllerBlock.FORMED, false),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    protected void serverTick() {
        // Tick logic here — only runs when isFormed() == true
    }
}

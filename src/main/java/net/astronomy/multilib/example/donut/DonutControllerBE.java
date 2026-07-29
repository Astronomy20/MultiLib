package net.astronomy.multilib.example.donut;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.callback.MultiblockFormedContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Test/demo controller BlockEntity for the multilib:example_donut structure (see DonutExampleSetup).
 */
public class DonutControllerBE extends AbstractMultiblockControllerBE {

    public DonutControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // Referenced by DonutExampleSetup's BlockEntityType.Builder.
    public static DonutControllerBE create(BlockPos pos, BlockState state) {
        return new DonutControllerBE(DonutExampleSetup.CONTROLLER_BE_TYPE, pos, state);
    }

    @Override
    protected void onFormed(MultiblockFormedContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Donut structure formed at {}", ctx.instance().getOrigin());
        BlockState myState = getBlockState();
        if (myState.hasProperty(DonutControllerBlock.FORMED)) {
            ctx.level().setBlock(worldPosition, myState.setValue(DonutControllerBlock.FORMED, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    protected void onBroken(MultiblockBrokenContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Donut structure broken, removed pos: {}", ctx.removedPos());
        BlockState myState = getBlockState();
        if (myState.hasProperty(DonutControllerBlock.FORMED)) {
            ctx.level().setBlock(worldPosition, myState.setValue(DonutControllerBlock.FORMED, false),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}

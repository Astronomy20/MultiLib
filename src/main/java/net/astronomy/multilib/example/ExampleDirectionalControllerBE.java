package net.astronomy.multilib.example;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.callback.MultiblockFormedContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Test/demo controller BlockEntity for the multilib:example_directional structure (see ExampleSetup).
 */
public class ExampleDirectionalControllerBE extends AbstractMultiblockControllerBE {

    public ExampleDirectionalControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        setValidationInterval(100);
    }

    // Referenced by ExampleSetup's BlockEntityType.Builder - same lazy-lookup reasoning as
    // ExampleControllerBE.create.
    public static ExampleDirectionalControllerBE create(BlockPos pos, BlockState state) {
        return new ExampleDirectionalControllerBE(ExampleSetup.DIRECTIONAL_CONTROLLER_BE_TYPE, pos, state);
    }

    @Override
    protected void onFormed(MultiblockFormedContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Example directional structure formed at {}", ctx.instance().getOrigin());
    }

    @Override
    protected void onBroken(MultiblockBrokenContext ctx) {
        MultiLib.LOGGER.info("[MultiLib] Example directional structure broken, removed pos: {}", ctx.removedPos());
    }

    @Override
    protected void serverTick() {
        // Tick logic here - only runs when isFormed() == true
    }
}

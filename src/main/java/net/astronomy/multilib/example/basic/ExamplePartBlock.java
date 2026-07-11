package net.astronomy.multilib.example.basic;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockPartBlock;

/**
 * Test/demo part block for the {@code multilib:example} structure (see {@link BasicExampleSetup}).
 * Becomes invisible (MODEL_HIDDEN) while the structure is formed; the controller renders the
 * full structure model in its place via {@link ExampleControllerBlock}'s {@code formed} state.
 */
public class ExamplePartBlock extends AbstractMultiblockPartBlock {

    public ExamplePartBlock(Properties properties) {
        super(properties);
    }
}

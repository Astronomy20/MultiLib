package net.astronomy.multilib.pattern.type;

import net.astronomy.multilib.pattern.PatternAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * MultiBlockPattern — represents a base for multi-block machines or activations.
 */
public class MultiBlockPattern implements PatternAction {

    private final boolean clearAfterActivation;

    public MultiBlockPattern(boolean clearAfterActivation) {
        this.clearAfterActivation = clearAfterActivation;
    }

    @Override
    public void onMatch(ServerLevel level, BlockPos origin) {
        if (clearAfterActivation) {
            // You can override this to get the right pattern reference,
            // or let the registry invoke it with a known pattern.
        }
    }
}
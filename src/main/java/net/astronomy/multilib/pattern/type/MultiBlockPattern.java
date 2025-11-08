package net.astronomy.multilib.pattern.type;

import net.astronomy.multilib.pattern.PatternAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * MultiBlockPattern — represents a base for multi-block machines or activations.
 */
public class MultiBlockPattern implements PatternAction {

    public MultiBlockPattern() {}

    @Override
    public void onMatch(ServerLevel level, BlockPos origin) {

    }
}
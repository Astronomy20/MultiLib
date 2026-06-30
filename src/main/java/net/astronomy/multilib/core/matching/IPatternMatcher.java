package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface IPatternMatcher {
    MatchResult matches(ServerLevel level, BlockPos activationPos, MultiblockDefinition definition);
}

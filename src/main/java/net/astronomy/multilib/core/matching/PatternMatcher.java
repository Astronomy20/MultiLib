package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class PatternMatcher {

    private static final ShapedMatcher SHAPED = new ShapedMatcher();
    private static final ShapelessMatcher SHAPELESS = new ShapelessMatcher();
    private static final FunctionalMatcher FUNCTIONAL = new FunctionalMatcher();

    public static MatchResult matches(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        if (definition.isShapeless()) {
            return SHAPELESS.matches(level, placedPos, definition);
        }
        if (definition.getPatternProvider().isPresent()) {
            return FUNCTIONAL.matches(level, placedPos, definition);
        }
        return SHAPED.matches(level, placedPos, definition);
    }
}

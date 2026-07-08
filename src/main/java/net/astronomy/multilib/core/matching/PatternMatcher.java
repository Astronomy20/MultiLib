package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class PatternMatcher {

    private static final ShapedMatcher SHAPED = new ShapedMatcher();
    private static final ShapelessMatcher SHAPELESS = new ShapelessMatcher();
    private static final FunctionalMatcher FUNCTIONAL = new FunctionalMatcher();

    /**
     * F12 step B: {@code definition} is always the registered (parent) definition - derived variant
     * definitions are never registered on their own (see {@link MultiblockDefinition#getVariantDefinitions()}).
     * Tries {@link MultiblockDefinition#getAllVariants()} in declaration order (parent first), dispatching
     * each one to the matcher its OWN flags call for - a variant can be shaped while another is shapeless,
     * since each is a full, independent {@code MultiblockDefinition}. For a legacy definition with no
     * variants, {@code getAllVariants()} is {@code [definition]}, so this is byte-identical to the old
     * single-dispatch behavior.
     * <p>
     * On the first {@link MatchResult.Success}, the result's {@link MatchData} is re-stamped with that
     * variant's {@link MultiblockDefinition#getVariantName()} so downstream code (instance persistence,
     * the wrench upgrade path) knows which variant actually matched.
     * <p>
     * If every variant fails, the FIRST (parent) variant's {@link MatchResult.Failure} is returned rather
     * than, say, the "closest" one - its report describes the primary/parent geometry, which is what the
     * ghost overlay and other mismatch-diagnostic tooling is written to expect and display.
     */
    public static MatchResult matches(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        MatchResult firstFailure = null;
        for (MultiblockDefinition variant : definition.getAllVariants()) {
            MatchResult result = dispatch(level, placedPos, variant);
            if (result instanceof MatchResult.Success success) {
                return new MatchResult.Success(success.data().withVariant(variant.getVariantName()));
            }
            if (firstFailure == null) {
                firstFailure = result;
            }
        }
        return firstFailure;
    }

    private static MatchResult dispatch(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        if (definition.isShapeless()) {
            return SHAPELESS.matches(level, placedPos, definition);
        }
        if (definition.getPatternProvider().isPresent()) {
            return FUNCTIONAL.matches(level, placedPos, definition);
        }
        return SHAPED.matches(level, placedPos, definition);
    }
}

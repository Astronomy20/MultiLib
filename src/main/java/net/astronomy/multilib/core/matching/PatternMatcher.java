package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PatternMatcher {

    private static final ShapedMatcher SHAPED = new ShapedMatcher();
    private static final ShapelessMatcher SHAPELESS = new ShapelessMatcher();
    private static final FunctionalMatcher FUNCTIONAL = new FunctionalMatcher();

    /**
     * Attempt order for {@link #matches}: explicit {@link MultiblockDefinition#getPriority()} wins
     * first (higher tries first), and only when priority ties (the common case - every variant
     * defaults to 0) does {@link #matchVolume(MultiblockDefinition)} break the tie, largest first.
     * Without this, a "nested" set of variants sharing one core/origin - e.g. small/medium/large tank
     * shells built one inside the next - would always lock onto whichever is declared FIRST that its
     * footprint physically satisfies, since {@code Success} short-circuits the search: declaring the
     * small variant before the large one means building the large structure still matches (and gets
     * stuck on) the small variant, because the small pattern's cells are a subset of what's physically
     * there. Trying the largest candidate first means the biggest structure actually present wins,
     * independent of the order the author happened to write {@code .variant(...)} calls in. Ties within
     * equal priority AND equal volume keep declaration order, since {@link java.util.Collections#sort}
     * is stable.
     */
    private static final Comparator<MultiblockDefinition> MATCH_ORDER =
            Comparator.<MultiblockDefinition>comparingInt(MultiblockDefinition::getPriority).reversed()
                    .thenComparing(Comparator.comparingLong(PatternMatcher::matchVolume).reversed());

    /**
     * F12 step B: {@code definition} is always the registered (parent) definition - derived variant
     * definitions are never registered on their own (see {@link MultiblockDefinition#getVariantDefinitions()}).
     * Tries {@link MultiblockDefinition#getAllVariants()} in {@link #MATCH_ORDER} (priority desc, then
     * bounding-volume desc), dispatching each one to the matcher its OWN flags call for - a variant can
     * be shaped while another is shapeless, since each is a full, independent {@code MultiblockDefinition}.
     * For a legacy definition with no variants, {@code getAllVariants()} is {@code [definition]}, so this
     * is byte-identical to the old single-dispatch behavior.
     * <p>
     * On the first {@link MatchResult.Success}, the result's {@link MatchData} is re-stamped with that
     * variant's {@link MultiblockDefinition#getVariantName()} so downstream code (instance persistence,
     * the wrench upgrade path) knows which variant actually matched.
     * <p>
     * If every variant fails, the PARENT ({@code definition} itself, i.e. {@code getAllVariants().get(0)}
     * before sorting)'s {@link MatchResult.Failure} is returned rather than, say, whichever failed last in
     * match-attempt order - its report describes the primary/parent geometry, which is what the ghost
     * overlay and other mismatch-diagnostic tooling is written to expect and display. This is independent
     * of {@link #MATCH_ORDER}, which only affects the order candidates are TRIED in, not which failure is
     * reported when none succeed.
     */
    public static MatchResult matches(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        List<MultiblockDefinition> variants = new ArrayList<>(definition.getAllVariants());
        variants.sort(MATCH_ORDER);

        MatchResult parentFailure = null;
        for (MultiblockDefinition variant : variants) {
            MatchResult result = dispatch(level, placedPos, variant);
            if (result instanceof MatchResult.Success success) {
                return new MatchResult.Success(success.data().withVariant(variant.getVariantName()));
            }
            if (variant == definition) {
                parentFailure = result;
            }
        }
        return parentFailure;
    }

    /**
     * Approximate matching footprint used only to order match attempts (see {@link #MATCH_ORDER}) -
     * not a precise volume for shapeless definitions, since their actual formed size is only known
     * after a successful flood fill. Shapeless uses its declared {@code maxSize} (the largest it could
     * possibly claim); a {@link net.astronomy.multilib.api.pattern.PatternProvider}-based definition
     * uses the same bounding-box-or-provider-size resolution {@link FunctionalMatcher} itself uses;
     * everything else (plain layer-based) derives width/depth/height straight from the declared layers.
     */
    private static long matchVolume(MultiblockDefinition definition) {
        if (definition.isShapeless()) {
            return volume(definition.getShapelessMaxSize());
        }
        if (definition.getPatternProvider().isPresent()) {
            Vec3i bb = definition.getBoundingBox();
            Vec3i size = !bb.equals(Vec3i.ZERO) ? bb : definition.getPatternProvider().get().getSize();
            return volume(size);
        }
        int height = definition.getLayers().size();
        int depth = 0, width = 0;
        for (List<String> layer : definition.getLayers()) {
            depth = Math.max(depth, layer.size());
            for (String row : layer) {
                width = Math.max(width, row.length());
            }
        }
        return (long) height * depth * width;
    }

    private static long volume(Vec3i v) {
        return (long) v.getX() * v.getY() * v.getZ();
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

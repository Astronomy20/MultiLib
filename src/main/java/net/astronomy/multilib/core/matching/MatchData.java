package net.astronomy.multilib.core.matching;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import java.util.Map;
import java.util.Set;

public record MatchData(
        BlockPos origin,
        TransformData transform,
        Set<BlockPos> positions,
        Map<Character, Set<BlockPos>> symbolPositions,
        Vec3i actualDimensions,
        String variantName
) {
    /**
     * F12 step A/B: the three matchers (Shaped/Shapeless/Functional) all construct {@code MatchData}
     * through this 5-arg constructor and must stay untouched, so it is kept working exactly as before -
     * it stamps the legacy/parent variant name "default". {@link PatternMatcher} is the only place that
     * needs a non-default variant name, and it does so via {@link #withVariant(String)} after a matcher
     * has already produced its result.
     */
    public MatchData(BlockPos origin, TransformData transform,
                     Set<BlockPos> positions, Map<Character, Set<BlockPos>> symbolPositions,
                     Vec3i actualDimensions) {
        this(origin, transform, positions, symbolPositions, actualDimensions, "default");
    }

    public MatchData(BlockPos origin, TransformData transform,
                     Set<BlockPos> positions, Map<Character, Set<BlockPos>> symbolPositions) {
        this(origin, transform, positions, symbolPositions, Vec3i.ZERO, "default");
    }

    /** Copy of this data stamped with a different variant name - used by {@link PatternMatcher} once it knows which variant-definition actually matched. */
    public MatchData withVariant(String variantName) {
        return new MatchData(origin, transform, positions, symbolPositions, actualDimensions, variantName);
    }
}

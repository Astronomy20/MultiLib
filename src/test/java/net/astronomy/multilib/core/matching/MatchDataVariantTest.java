package net.astronomy.multilib.core.matching;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The F12 compatibility contract on {@link MatchData}: the pre-variant constructor arity (what the
 * three matchers still call) must default the variant to "default", and {@link MatchData#withVariant}
 * must stamp a new name without disturbing any other component - that is what lets the matchers stay
 * untouched while {@code PatternMatcher} re-stamps their results per tried variant.
 */
class MatchDataVariantTest {

    private static MatchData sample() {
        return new MatchData(
                BlockPos.ZERO,
                new TransformData(1, false, "Y"),
                Set.of(new BlockPos(1, 2, 3)),
                Map.of('A', Set.of(new BlockPos(1, 2, 3))),
                new Vec3i(3, 2, 3));
    }

    @Test
    void legacyConstructorDefaultsVariant() {
        assertEquals("default", sample().variantName());
    }

    @Test
    void withVariantStampsNameOnly() {
        MatchData original = sample();
        MatchData stamped = original.withVariant("tall");

        assertEquals("tall", stamped.variantName());
        assertEquals(original.origin(), stamped.origin());
        assertEquals(original.transform(), stamped.transform());
        assertEquals(original.positions(), stamped.positions());
        assertEquals(original.symbolPositions(), stamped.symbolPositions());
        assertEquals(original.actualDimensions(), stamped.actualDimensions());
    }
}

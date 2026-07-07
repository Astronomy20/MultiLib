package net.astronomy.multilib.api.tier;

import java.util.Map;

/**
 * A resolved tier level for a single pattern symbol: the {@code name} declared via
 * {@code MultiblockBuilder#tier(...)}, its declaration-order {@code ordinal} (0 = lowest), and the
 * declared {@code stats} of the matched {@link net.astronomy.multilib.api.definition.TierSpec}. Kept
 * separate from {@code TierSpec} since a spec is "what block(s) count as this tier" while a
 * {@code TierLevel} is just the resolved identity/rank/stats a caller reads - no block set or tag
 * baggage needed once a match has already happened. {@code stats} is carried over (rather than dropped
 * like the block set/tag are) because it's exactly the data downstream behavior code wants once
 * resolution has picked a winner - see {@link MultiblockTierResolution} for symbol-level and
 * aggregated access.
 */
public record TierLevel(String name, int ordinal, Map<String, Double> stats) {

    public TierLevel {
        stats = Map.copyOf(stats);
    }

    /** Pre-stats-map constructor, kept for source compatibility (defaults to no stats). */
    public TierLevel(String name, int ordinal) {
        this(name, ordinal, Map.of());
    }

    public boolean isAtLeast(TierLevel other) {
        return this.ordinal >= other.ordinal;
    }
}

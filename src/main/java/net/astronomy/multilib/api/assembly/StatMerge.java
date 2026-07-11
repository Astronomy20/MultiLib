package net.astronomy.multilib.api.assembly;

import java.util.Collection;

/**
 * Explicit rule for combining a per-member tier stat into a single assembly-level value. Never
 * guessed: the assembly author declares which rule applies to each stat key (see
 * {@link AssemblyBuilder#aggregateStat}). Mirrors the "merge rules are explicit, never inferred"
 * stance of the Fase 11 tier system.
 */
public enum StatMerge {
    SUM,
    MIN,
    MAX,
    AVG;

    /** Applies this merge rule to a collection of member values. Returns 0 for an empty input. */
    public double apply(Collection<Double> values) {
        if (values.isEmpty()) return 0.0;
        return switch (this) {
            case SUM -> values.stream().mapToDouble(Double::doubleValue).sum();
            case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        };
    }
}

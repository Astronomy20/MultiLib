package net.astronomy.multilib.api.tier;

import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;

/**
 * Immutable snapshot of the tier resolved for each tiered symbol of a formed multiblock instance, at
 * the moment it was computed (see {@link MultiblockTier#get}). Only holds symbols that actually have a
 * declared tier list AND a match against the currently placed block - a tiered symbol whose placed
 * block matches none of its declared {@code TierSpec}s is simply absent here rather than treated as an
 * error, since resolution just reports what it found.
 */
public record MultiblockTierResolution(Map<Character, TierLevel> tierBySymbol) {

    public MultiblockTierResolution {
        tierBySymbol = Map.copyOf(tierBySymbol);
    }

    public Optional<TierLevel> tierForSymbol(char symbol) {
        return Optional.ofNullable(tierBySymbol.get(symbol));
    }

    /**
     * Overall tier of the structure, using the weakest resolved symbol (minimum ordinal) as the
     * limiting factor - a structure is only as strong as its lowest-tier part.
     */
    public Optional<TierLevel> overallTier() {
        return overallTier(BinaryOperator.minBy((a, b) -> Integer.compare(a.ordinal(), b.ordinal())));
    }

    /**
     * Like {@link #overallTier()}, but lets the caller pick a different reduction across the resolved
     * symbols (e.g. the highest tier present, or some custom average) instead of the "weakest part"
     * default.
     */
    public Optional<TierLevel> overallTier(BinaryOperator<TierLevel> reducer) {
        return tierBySymbol.values().stream().reduce(reducer);
    }

    /**
     * Raw stat map declared on the tier resolved for {@code symbol} (see {@code TierSpec#stats}), or
     * an empty map if {@code symbol} has no resolved tier or that tier declared no stats. This is the
     * "no surprises" access path: no merge rule is applied, the caller sees exactly what one symbol's
     * matched {@link TierSpec} declared.
     */
    public Map<String, Double> statsFor(char symbol) {
        return tierForSymbol(symbol).map(TierLevel::stats).orElse(Map.of());
    }

    /**
     * Folds {@code key}'s value across every resolved symbol's stats using {@code merger}, starting
     * from {@code identity} - symbols that don't declare {@code key} are simply skipped (not treated as
     * contributing {@code identity} again). This is the escape hatch for when more than one symbol
     * declares the same stat key and there IS a sensible way to combine them: e.g.
     * {@code combinedStats("speed", Double::sum, 0.0)} for additive throughput across several input-port
     * symbols, or {@code combinedStats("heat_resistance", Math::min, Double.POSITIVE_INFINITY)} for a
     * "weakest link" cap. Deliberately makes the caller choose the rule rather than MultiLib guessing
     * one (sum vs. min vs. last-wins are all defensible depending on the stat's semantics, and guessing
     * wrong silently would be worse than requiring an explicit choice here).
     */
    public double combinedStats(String key, DoubleBinaryOperator merger, double identity) {
        double acc = identity;
        for (TierLevel level : tierBySymbol.values()) {
            Double value = level.stats().get(key);
            if (value != null) {
                acc = merger.applyAsDouble(acc, value);
            }
        }
        return acc;
    }

    /**
     * Convenience for the common case where {@code key} is only ever expected to resolve on a single
     * symbol (e.g. a stat that only one kind of casing declares): returns that one value, or
     * {@code fallback} if no resolved symbol declares {@code key}. Throws {@link IllegalStateException}
     * if more than one resolved symbol declares {@code key} - silently picking one (last-wins, first-wins,
     * or summing) would hide a definition bug (two symbols both claiming the same stat name) behind a
     * plausible-looking number. Use {@link #combinedStats(String, DoubleBinaryOperator, double)} instead
     * once that's a real, intended possibility for the stat in question.
     */
    public double stat(String key, double fallback) {
        Double found = null;
        for (TierLevel level : tierBySymbol.values()) {
            Double value = level.stats().get(key);
            if (value == null) continue;
            if (found != null) {
                throw new IllegalStateException(
                        "Stat '" + key + "' resolves on more than one symbol - use combinedStats(...) "
                                + "to pick a merge rule explicitly instead of stat(...).");
            }
            found = value;
        }
        return found != null ? found : fallback;
    }
}

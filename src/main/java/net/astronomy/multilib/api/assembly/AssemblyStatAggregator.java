package net.astronomy.multilib.api.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies an assembly's declared {@link StatMerge} rules to per-member stat maps. The merge rule for
 * each key is taken from {@link AssemblyDefinition#getAggregateStats()} — explicit, never inferred.
 * The caller supplies each member's stats (e.g. from that member's own tier resolution), keeping the
 * source of member stats decoupled from the merge machinery.
 */
public final class AssemblyStatAggregator {

    private AssemblyStatAggregator() {}

    /** Aggregates every stat key the definition declares a merge rule for. */
    public static Map<String, Double> aggregate(AssemblyDefinition def, Collection<Map<String, Double>> perMemberStats) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, StatMerge> entry : def.getAggregateStats().entrySet()) {
            result.put(entry.getKey(), merge(entry.getKey(), entry.getValue(), perMemberStats));
        }
        return result;
    }

    /** Aggregates a single stat key using the definition's declared rule, or {@code fallback} if none. */
    public static double aggregateStat(AssemblyDefinition def, String key,
                                       Collection<Map<String, Double>> perMemberStats, double fallback) {
        StatMerge rule = def.getAggregateStats().get(key);
        if (rule == null) return fallback;
        return merge(key, rule, perMemberStats);
    }

    private static double merge(String key, StatMerge rule, Collection<Map<String, Double>> perMemberStats) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Double> stats : perMemberStats) {
            Double v = stats.get(key);
            if (v != null) values.add(v);
        }
        return rule.apply(values);
    }
}

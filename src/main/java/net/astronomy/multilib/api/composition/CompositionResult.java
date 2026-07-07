package net.astronomy.multilib.api.composition;

import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable introspection result for an already-formed multiblock structure - what's actually
 * sitting at every tracked position, grouped by pattern symbol. {@link #countByBlock()} and
 * {@link #countForSymbol(char)} are the default, aggregated view most callers want (e.g. "how many
 * iron blocks make up this structure"); {@link #ingredientMatch()} is the escape hatch down to raw
 * per-position data for callers that need more than a count (e.g. highlighting specific positions).
 */
public final class CompositionResult {

    private final Map<Character, List<BlockIngredientMatch>> matchesBySymbol;

    public CompositionResult(Map<Character, List<BlockIngredientMatch>> matchesBySymbol) {
        Map<Character, List<BlockIngredientMatch>> copy = new HashMap<>();
        matchesBySymbol.forEach((symbol, matches) -> copy.put(symbol, List.copyOf(matches)));
        this.matchesBySymbol = Map.copyOf(copy);
    }

    public Map<Character, List<BlockIngredientMatch>> matchesBySymbol() {
        return matchesBySymbol;
    }

    /** Aggregated block counts across every symbol in the structure. */
    public Map<Block, Integer> countByBlock() {
        Map<Block, Integer> counts = new HashMap<>();
        for (List<BlockIngredientMatch> matches : matchesBySymbol.values()) {
            for (BlockIngredientMatch match : matches) {
                Block block = match.actualState().getBlock();
                counts.merge(block, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Block counts restricted to the positions belonging to a single pattern symbol. */
    public Map<Block, Integer> countForSymbol(char symbol) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockIngredientMatch match : matchesBySymbol.getOrDefault(symbol, List.of())) {
            Block block = match.actualState().getBlock();
            counts.merge(block, 1, Integer::sum);
        }
        return counts;
    }

    /** Total number of tracked positions across every symbol. */
    public int totalCount() {
        int total = 0;
        for (List<BlockIngredientMatch> matches : matchesBySymbol.values()) {
            total += matches.size();
        }
        return total;
    }

    /**
     * Flattens every symbol's matches into a single list - the raw per-position extension point
     * beneath {@link #countByBlock()}/{@link #countForSymbol(char)}, which are the default aggregation.
     */
    public List<BlockIngredientMatch> ingredientMatch() {
        List<BlockIngredientMatch> all = new ArrayList<>();
        for (List<BlockIngredientMatch> matches : matchesBySymbol.values()) {
            all.addAll(matches);
        }
        return List.copyOf(all);
    }
}

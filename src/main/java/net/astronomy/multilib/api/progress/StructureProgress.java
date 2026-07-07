package net.astronomy.multilib.api.progress;

import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A snapshot of how complete a multiblock structure is, computed fresh from the current world state
 * (not cached) - see {@link MultiblockProgressAPI#compute}.
 */
public record StructureProgress(int totalRequired, List<MissingBlock> missing) {

    public int missingCount() { return missing.size(); }

    public int placedCount() { return totalRequired - missing.size(); }

    public boolean isComplete() { return missing.isEmpty(); }

    /** Missing positions grouped by expected block type with counts - a ready-to-display "shopping list". */
    public Map<Block, Long> missingCountsByBlock() {
        return missing.stream().collect(Collectors.groupingBy(
                m -> m.expectedState().getBlock(), Collectors.counting()));
    }
}

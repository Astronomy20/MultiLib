package net.astronomy.multilib.api.composition;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports what a multiblock structure is actually built out of, so a consuming mod can show its
 * own "bill of materials" (a breakdown of which blocks make up the structure and how many of each)
 * without reimplementing structure introspection. Read-only - this never places, breaks, or
 * otherwise changes anything.
 * <p>
 * Unlike {@link net.astronomy.multilib.api.progress.MultiblockProgressAPI}, this operates on an
 * already-formed {@link MultiblockInstance} rather than a bare core position: there's no pattern
 * matching or orientation detection to do, since the instance already tracks which world position
 * belongs to which pattern symbol via {@link MultiblockInstance#getPositionsFor(char)}.
 */
public final class MultiblockComposition {

    private MultiblockComposition() {}

    /**
     * Computes the composition of the formed structure described by {@code ctx}.
     */
    public static CompositionResult compute(MultiblockContext ctx) {
        return compute(ctx.level(), ctx.instance());
    }

    /**
     * Computes the composition of {@code instance} by reading, for every pattern symbol declared on
     * its resolved definition, the block state currently sitting at each of the instance's tracked
     * positions for that symbol.
     */
    public static CompositionResult compute(ServerLevel level, MultiblockInstance instance) {
        MultiblockDefinition definition = MultiblockRegistry.get(instance.getDefinitionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Definition not found: " + instance.getDefinitionId()));

        Map<Character, List<BlockIngredientMatch>> matchesBySymbol = new HashMap<>();

        for (char symbol : definition.getBlockMap().keySet()) {
            List<BlockIngredientMatch> matches = new ArrayList<>();
            for (BlockPos pos : instance.getPositionsFor(symbol)) {
                BlockState actualState = level.getBlockState(pos);
                matches.add(new BlockIngredientMatch(pos, symbol, actualState));
            }
            matchesBySymbol.put(symbol, matches);
        }

        return new CompositionResult(matchesBySymbol);
    }
}

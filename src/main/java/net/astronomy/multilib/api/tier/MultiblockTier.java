package net.astronomy.multilib.api.tier;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.TierSpec;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the tier of each tiered symbol of a formed multiblock against the blocks actually placed
 * in the world right now. Never cached: a {@link TierSpec} can be backed by a {@code TagKey}, and tag
 * membership can change on any {@code /reload}, so there's nothing safe to invalidate - every call
 * just re-reads current world state.
 */
public final class MultiblockTier {

    private MultiblockTier() {}

    public static MultiblockTierResolution get(MultiblockContext ctx) {
        return get(ctx.level(), ctx.instance(), ctx.definition());
    }

    public static MultiblockTierResolution get(ServerLevel level, MultiblockInstance instance, MultiblockDefinition definition) {
        Map<Character, TierLevel> resolved = new HashMap<>();

        for (Map.Entry<Character, List<TierSpec>> entry : definition.getTierSpecs().entrySet()) {
            char symbol = entry.getKey();
            List<TierSpec> specs = entry.getValue();

            TierLevel weakest = null;
            for (BlockPos pos : instance.getPositionsFor(symbol)) {
                Block block = level.getBlockState(pos).getBlock();
                for (TierSpec spec : specs) {
                    if (spec.matches(block)) {
                        // A symbol can occupy multiple positions (e.g. a ring of casings); if those
                        // positions hold blocks of different declared tiers, the structure is only as
                        // strong as the weakest one placed for that symbol, so keep the lowest ordinal
                        // match found across all of the symbol's positions.
                        if (weakest == null || spec.ordinal() < weakest.ordinal()) {
                            weakest = new TierLevel(spec.name(), spec.ordinal(), spec.stats());
                        }
                        break;
                    }
                }
            }

            if (weakest != null) {
                resolved.put(symbol, weakest);
            }
        }

        return new MultiblockTierResolution(resolved);
    }
}

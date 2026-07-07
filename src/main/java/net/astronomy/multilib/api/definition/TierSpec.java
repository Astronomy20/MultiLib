package net.astronomy.multilib.api.definition;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * One named tier level declared via {@link MultiblockBuilder#tier(char, String, Block...)} or
 * {@link MultiblockBuilder#tier(char, String, TagKey)} for a given pattern symbol. {@code ordinal} is
 * the declaration order for that symbol (0 = first/lowest tier declared) - tiers are meant to be
 * declared low-to-high, and {@code ordinal} is what lets a caller compare two tiers ("is this at least
 * an advanced casing?") without depending on the tier names themselves.
 * <p>
 * Backed by either an explicit block set or a {@link TagKey} (never both at once in practice, though
 * nothing stops declaring both): the tag form lets third-party addons contribute their own blocks to a
 * tier via datapack, without the multiblock's own definition needing to know about them ahead of time.
 * Resolved against the actual placed block at query time (see {@code MultiblockTier}) rather than
 * cached, since tag membership can change on any {@code /reload}.
 * <p>
 * {@code stats} is the GregTech-coil-style glue between a tier and actual machine behavior (e.g.
 * {@code "speed" -> 2.0}, {@code "energy_capacity" -> 10000.0}) - arbitrary numeric knobs a controller's
 * tick/validator logic can read back via {@code MultiblockTierResolution} once the tier is resolved
 * against the placed blocks. Empty by default so existing callers that never cared about stats are
 * unaffected.
 */
public record TierSpec(String name, int ordinal, Set<Block> blocks, @Nullable TagKey<Block> tag, Map<String, Double> stats) {

    public TierSpec {
        stats = Map.copyOf(stats);
    }

    /**
     * Pre-stats-map constructor, kept for source/behavioral compatibility with code written before
     * {@code stats} existed (defaults to no stats).
     */
    public TierSpec(String name, int ordinal, Set<Block> blocks, @Nullable TagKey<Block> tag) {
        this(name, ordinal, blocks, tag, Map.of());
    }

    public boolean matches(Block block) {
        if (blocks.contains(block)) return true;
        return tag != null && block.builtInRegistryHolder().is(tag);
    }
}

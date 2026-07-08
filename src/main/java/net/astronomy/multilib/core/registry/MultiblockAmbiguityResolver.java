package net.astronomy.multilib.core.registry;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Single shared answer to "which registered definition should {@code pos}'s block be treated as", used
 * by every feature that needs to resolve one definition out of {@link MultiblockRegistry}'s (possibly
 * multi-candidate) list for a block type - the ghost overlay and auto-place previously each carried
 * their own near-identical copy of this loop, which is exactly the kind of silent-drift risk already
 * seen once between the pattern matchers (see {@code core.matching.ShapedMatcher}/{@code
 * FunctionalMatcher}).
 * <p>
 * Resolution order: filter {@link MultiblockRegistry#getCandidatesFor} down to whatever the caller's
 * {@code matcher} predicate accepts (e.g. "core symbol only" for auto-place, "core or activation" for
 * the ghost overlay) - if that leaves 0 or 1 candidate, there's nothing to disambiguate and priority
 * order (already baked into {@code getCandidatesFor}'s own sort) decides it same as always. Only when
 * genuinely ambiguous (2+ eligible candidates) is {@link MultiblockPreferenceTracker} consulted; a
 * stale preference (the stored id is no longer among the eligible candidates - block type changed,
 * definition removed) is silently ignored and falls through to priority order, exactly like an unset
 * preference - this must never be worse than today's behavior, only ever better.
 */
public final class MultiblockAmbiguityResolver {

    private MultiblockAmbiguityResolver() {}

    public static Optional<MultiblockDefinition> resolve(ServerLevel level, BlockPos pos,
                                                           BiPredicate<MultiblockDefinition, BlockState> matcher) {
        BlockState state = level.getBlockState(pos);
        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(state.getBlock()).stream()
                .filter(def -> matcher.test(def, state))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        if (candidates.size() == 1) return Optional.of(candidates.get(0));

        Optional<ResourceLocation> preferred = MultiblockPreferenceTracker.get(level).get(pos);
        if (preferred.isPresent()) {
            for (MultiblockDefinition def : candidates) {
                if (def.getId().equals(preferred.get())) return Optional.of(def);
            }
        }
        // candidates is already priority-sorted by getCandidatesFor - the first entry is today's
        // (unchanged) resolution when no valid preference is set.
        return Optional.of(candidates.get(0));
    }

    /**
     * Every candidate {@code matcher} accepts at {@code pos}, priority-sorted - for tools (e.g. the
     * preference wrench) that need to show a player the full set of what's ambiguous there, not just
     * the winner. Takes a plain {@link Level} (not {@link ServerLevel}) deliberately: unlike
     * {@link #resolve}, this never touches {@link MultiblockPreferenceTracker} (server-only
     * {@code SavedData}), so it works identically from client-side code building a picker's candidate
     * list (mirroring the same {@code MultiblockRegistry.getCandidatesFor(...)} check the ghost
     * overlay/auto-place client input handlers already run) and from server-side validation of a
     * client's selection - both against the exact same logic, so the two can never drift apart.
     */
    public static List<MultiblockDefinition> candidatesAt(Level level, BlockPos pos,
                                                            BiPredicate<MultiblockDefinition, BlockState> matcher) {
        BlockState state = level.getBlockState(pos);
        return MultiblockRegistry.getCandidatesFor(state.getBlock()).stream()
                .filter(def -> matcher.test(def, state))
                .toList();
    }
}

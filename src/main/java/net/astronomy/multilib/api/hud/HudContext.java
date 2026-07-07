package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * Everything a {@link MultiblockHudProvider} needs to inspect one formed multiblock, from the
 * perspective of one player looking at one block right now: the level and the exact block position
 * looked at (which need not be the instance's core - any member block resolves to the same instance),
 * the resolved {@link MultiblockInstance}/{@link MultiblockDefinition} pair, and the looking player.
 * Server-side only, same as everything else in this package - a viewer adapter builds one of these per
 * lookup via {@link #at} and hands it to {@link MultiblockHudRegistry#gatherEntries}.
 */
public record HudContext(
        ServerLevel level,
        BlockPos pos,
        MultiblockInstance instance,
        MultiblockDefinition definition,
        ServerPlayer player
) {

    /**
     * Resolves a {@code HudContext} for whatever multiblock instance (if any) occupies {@code pos}, via
     * {@link WorldMultiblockTracker#getInstancesAt}. Empty if no instance is there, or if the instance's
     * definition has since been unregistered (e.g. mid-{@code /reload}) - in both cases the caller
     * should fall back to {@link MultiblockHudRegistry#gatherUnformedEntries} instead.
     * <p>
     * More than one instance can theoretically occupy the same position (e.g. two overlapping
     * optional-symbol structures); this arbitrarily picks one via {@link Set#iterator()} rather than
     * merging entries from all of them, since {@link HudEntry} carries no "which structure is this
     * from" grouping and mixing two structures' status lines into one tooltip would be more confusing
     * than showing just one.
     */
    public static Optional<HudContext> at(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Set<MultiblockInstance> instances = WorldMultiblockTracker.get(level).getInstancesAt(pos);
        if (instances.isEmpty()) return Optional.empty();
        MultiblockInstance instance = instances.iterator().next();
        return MultiblockRegistry.get(instance.getDefinitionId())
                .map(definition -> new HudContext(level, pos, instance, definition, player));
    }
}

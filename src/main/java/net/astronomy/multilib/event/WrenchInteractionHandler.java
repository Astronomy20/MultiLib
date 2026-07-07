package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.WrenchInteractionEvent;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.tool.WrenchResult;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.registry.WrenchItemRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Makes {@code IMultiblockWrench}/{@code MultiLibAPI.registerWrenchItem} actually do something: any
 * right-click on a candidate activation/core block with a registered wrench item attempts formation
 * here, regardless of whether the item is a hand-written Java class or a data-driven/scripted one
 * that can't implement a custom interface.
 * <p>
 * Deliberately mechanism-only: no chat messages, no other player-facing side effects. What actually
 * happened is posted as a {@link WrenchInteractionEvent} - that's the entire feedback surface. Whether
 * (and how) to tell the player is up to whoever listens; see {@link WrenchFeedbackHandler} for the
 * library's own (dev-mode-gated) chat feedback, or the {@code MultiblockEvents.wrench(...)} KubeJS
 * event for scripts.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public class WrenchInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!WrenchItemRegistry.isWrench(event.getItemStack().getItem())) return;

        WrenchResult result = attemptWrenchInteraction(level, event.getPos(), player);
        NeoForge.EVENT_BUS.post(new WrenchInteractionEvent(level, event.getPos(), player, result));

        if (!(result instanceof WrenchResult.NotAMultiblock)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Attempts a wrench interaction at {@code pos} and reports what happened. Does not post
     * {@link WrenchInteractionEvent} itself - that's only done by {@link #onRightClickBlock} for the
     * real right-click path, so a caller invoking this directly (e.g. a custom wrench item that wants
     * the result without going through the global handler) doesn't get a duplicate event.
     */
    public static WrenchResult attemptWrenchInteraction(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Block block = level.getBlockState(pos).getBlock();
        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(block);
        MultiLib.LOGGER.debug("[MultiLib] Wrench used on {} at {}: {} candidate definition(s)",
                block, pos, candidates.size());

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        // Reported only if no candidate ends up matching at all - candidates are already priority-sorted
        // by MultiblockRegistry.getCandidatesFor, so this is the highest-priority candidate's own reason,
        // the most relevant one to show when several definitions share the same core/activation block and
        // none of them are a full match right now.
        WrenchResult.FormationFailed bestFailure = null;

        for (MultiblockDefinition definition : candidates) {
            // Accept either the activation block or the core block - they're often the same symbol,
            // but a structure can split them (e.g. activation = placed last, core = the controller).
            if (!definition.matchesActivationOrCore(level.getBlockState(pos))) continue;

            Set<MultiblockInstance> instances = tracker.getInstancesAt(pos);
            Optional<MultiblockInstance> ownInstance = instances.stream()
                    .filter(instance -> instance.getDefinitionId().equals(definition.getId()))
                    .findFirst();
            if (ownInstance.isPresent()) {
                return new WrenchResult.AlreadyFormed(ownInstance.get());
            }

            // Check the pattern before formationMode, not after: the wrench must stay useful as a
            // read-only "what's missing" diagnostic even on a structure that only ever forms
            // automatically (formationMode AUTOMATIC) - reporting ModeDisallowsWrench outright, before
            // ever looking at the pattern, hid that entirely. ModeDisallowsWrench is now reserved for
            // "this structure is actually complete, but this mode won't let a wrench be what finishes
            // it" - a materially different, still-useful signal.
            MatchResult matchResult = PatternMatcher.matches(level, pos, definition);
            if (!(matchResult instanceof MatchResult.Success)) {
                // Don't stop here - a lower-priority definition sharing the same core/activation block
                // may still match. Without this fallthrough, a modpack where several devs reused the same
                // block as a core (priorities not yet tuned) would report failure for the whole click the
                // moment the first candidate's pattern didn't match, even if a later one was satisfied.
                String reason = matchResult instanceof MatchResult.Failure failure
                        ? failure.report().summary() : "unknown";
                if (bestFailure == null) {
                    bestFailure = new WrenchResult.FormationFailed(definition, reason);
                }
                continue;
            }

            if (!definition.getFormationMode().allowsWrench()) {
                return new WrenchResult.ModeDisallowsWrench(definition);
            }

            int prevCount = tracker.getAllInstances().size();
            BlockActivationHandler.triggerFormationAt(level, pos, player);
            if (tracker.getAllInstances().size() > prevCount) {
                return new WrenchResult.Formed(definition);
            }
            // Pattern matched a moment ago but no instance appeared - something else (a custom
            // validator) rejected it.
            return new WrenchResult.FormationFailed(definition, "a validator rejected the formation attempt");
        }
        return bestFailure != null ? bestFailure : new WrenchResult.NotAMultiblock();
    }
}

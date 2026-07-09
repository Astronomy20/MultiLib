package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.IMultiblockPart;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.WrenchInteractionEvent;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.tool.WrenchResult;
import net.astronomy.multilib.core.matching.MatchData;
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
 * Makes {@code IMultiblockWrench}/{@code MultiLib.registerWrenchItem} actually do something: any
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
                MultiblockInstance existing = ownInstance.get();
                // Cheap guard: a legacy definition's getAllVariants() is just [definition], so a
                // structure with no declared variants pays nothing beyond this size() check - it always
                // falls straight through to the untouched AlreadyFormed path below.
                if (definition.getAllVariants().size() > 1) {
                    MatchResult reMatch = PatternMatcher.matches(level, pos, definition);
                    if (reMatch instanceof MatchResult.Success success
                            && !success.data().variantName().equals(existing.getVariant())) {
                        return upgradeVariant(level, tracker, definition, existing, success.data());
                    }
                }
                return new WrenchResult.AlreadyFormed(existing);
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

    /**
     * F12 step B: swaps an already-formed instance to a different variant of the SAME definition, in
     * place, reusing the existing UUID so anything keyed off it (ports, external references) keeps
     * working across the swap. Deliberately does not go through {@code BlockBreakHandler.handleBreak}
     * or {@code BlockActivationHandler}'s formation path: this is an upgrade of an already-standing
     * structure, not a teardown/rebuild, so {@code MultiblockBrokenEvent}/{@code MultiblockFormedEvent}
     * and the broken/formed callbacks are intentionally never fired - firing them would wipe controller/
     * contents state that formed-once semantics already assume survives, and would double-record a
     * formation that (from the player's perspective) never actually broke.
     * <p>
     * Only the per-part join/leave hooks are reconciled, and only for the positions that actually
     * changed between the two variants: newly-covered positions get {@code onJoinedStructure}, positions
     * the new variant no longer covers get {@code onLeftStructure} - positions common to both variants
     * are left untouched.
     */
    private static WrenchResult upgradeVariant(ServerLevel level, WorldMultiblockTracker tracker,
                                               MultiblockDefinition definition, MultiblockInstance oldInstance,
                                               MatchData newMatchData) {
        String fromVariant = oldInstance.getVariant();
        String toVariant = newMatchData.variantName();

        tracker.unregister(oldInstance.getId());
        MultiblockInstance newInstance = new MultiblockInstance(oldInstance.getId(), definition.getId(),
                newMatchData.origin(), newMatchData.transform(), newMatchData, oldInstance.getFormedBy());
        tracker.register(newInstance, definition);

        Set<BlockPos> oldPositions = oldInstance.getPositions();
        Set<BlockPos> newPositions = newInstance.getPositions();
        // formedProperty follows the same membership delta as the part hooks below: formation set it
        // to true on the old variant's members (BlockActivationHandler#handleFormation), and nothing
        // else would ever flip it for blocks that joined/left during an in-place upgrade - the full
        // formed/broken paths deliberately don't run here.
        String formedProperty = definition.getFormedProperty().orElse(null);

        for (BlockPos p : newPositions) {
            if (oldPositions.contains(p)) continue;
            if (formedProperty != null) {
                BlockActivationHandler.setFormedPropertyIfPresent(level, p, formedProperty, true);
            }
            if (level.getBlockEntity(p) instanceof IMultiblockPart part) {
                part.getMultiblockComponent().onJoinedStructure(newInstance);
            }
        }
        for (BlockPos p : oldPositions) {
            if (newPositions.contains(p)) continue;
            if (formedProperty != null) {
                BlockActivationHandler.setFormedPropertyIfPresent(level, p, formedProperty, false);
            }
            if (level.getBlockEntity(p) instanceof IMultiblockPart part) {
                part.getMultiblockComponent().onLeftStructure();
            }
        }

        return new WrenchResult.VariantChanged(definition.getId(), fromVariant, toVariant);
    }
}

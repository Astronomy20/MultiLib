package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockIndex;
import net.astronomy.multilib.core.devtool.MultiblockDevRegistry;
import net.astronomy.multilib.core.devtool.MultiblockDevTagSessionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global right-click listener that lets a player tag the core/activation symbol of the area they last
 * scanned with a Multiblock Dev Block, triggered by right-clicking a block while holding the dedicated
 * {@link MultiblockDevRegistry#DEV_WRENCH_ITEM} (see phase-10 design doc, "Design 3").
 * <p>
 * Originally this was sneak+right-click with any item (or empty-handed), so tagging never needed a
 * dedicated tool. That gesture turned out to be unreliable in practice: sneak+right-clicking a block that
 * also had its own use behavior - most commonly the dev block itself, whenever its scanned area happened
 * to include its own position - could tag and then immediately untag the same block on what looked like a
 * single click. Requiring a specific item removes the ambiguity: holding the wrench unconditionally
 * suppresses every other interaction on the clicked block (GUI opens, item placement, etc. - see the
 * unconditional {@code event.setCanceled} below, before any area/session check), so a wrench click is
 * never anything other than a tag attempt, and never falls through into a second, competing interaction
 * path on the same click.
 * <p>
 * Deliberately mechanism-only in terms of dependencies: this class doesn't decide the tagging outcome
 * itself, it only recognizes the trigger gesture, resolves which dev-block owns the active session,
 * and delegates to {@link MultiblockDevBlockEntity#tagPosition(BlockPos, BlockState)} for the actual
 * scan-derived logic (core vs. activation-duplicate). Chat feedback for the outcome lives here, same
 * as {@code WrenchInteractionHandler} keeps interaction mechanics separate from
 * {@code WrenchFeedbackHandler}'s chat feedback - except here there's no separate event to post, so
 * feedback is inlined directly.
 * <p>
 * Messages are intentionally {@link Component#literal(String)} only (no translation keys): this class
 * is developed in parallel with another task that is also appending keys to
 * {@code assets/multilib/lang/en_us.json}, and concurrent edits to the same JSON file from two agents
 * risk a merge conflict. Using literals avoids touching that file at all. If localization is wanted
 * later, these can be migrated to {@code Component.translatable} in a follow-up pass.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public class MultiblockDevTagHandler {

    /**
     * Last game-tick a given player's wrench click was actually processed, purely as a defense-in-depth
     * debounce against a single physical click somehow producing two {@code RightClickBlock} dispatches -
     * the exact mechanism behind the old tag-then-untag bug was never conclusively pinned down before the
     * gesture was replaced with a dedicated item, so this stays as a cheap safety net even though the new
     * item-gated trigger should no longer share a click with a competing interaction path.
     */
    private static final Map<UUID, Long> lastProcessedTick = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (MultiblockDevRegistry.DEV_WRENCH_ITEM == null
                || player.getMainHandItem().getItem() != MultiblockDevRegistry.DEV_WRENCH_ITEM) {
            return;
        }

        // Holding the wrench always suppresses whatever else this click would have done (opening a GUI,
        // placing a block, etc.) - set unconditionally, before the session/area checks below, so there's
        // no path where a wrench click both attempts a tag AND falls through to a normal interaction.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        long gameTime = level.getGameTime();
        Long lastTick = lastProcessedTick.put(player.getUUID(), gameTime);
        if (lastTick != null && lastTick == gameTime) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        MultiblockDevBlockEntity devBlockEntity = resolveDevBlock(level, player, clickedPos);
        if (devBlockEntity == null) {
            player.sendSystemMessage(Component.literal(
                    "[MultiLib] That position isn't inside any Multiblock Dev Block's area - stand its scan area over the block first."));
            return;
        }

        BlockState stateAtPos = level.getBlockState(clickedPos);

        // Deliberately NOT skipped even if this block is already the core of some other, real
        // multiblock - it must stay taggable here too (the dev-tool needs to work on real structures'
        // core blocks, not just plain scenery). The conflict this used to guard against (this cancel
        // not stopping the client-side ghost overlay trigger, which runs as its own independent
        // PlayerInteractEvent.RightClickBlock listener client-side) is instead handled over there, by
        // suppressing the ghost overlay specifically when the clicked position falls inside the
        // dev-block's active area preview - see GhostOverlayInputHandler.

        MultiblockDevBlockEntity.TagOutcome outcome = devBlockEntity.tagPosition(clickedPos, stateAtPos);

        // The currently-open GUI (if any) shows the core/activation line from its own MultiblockDevMenu
        // state, which is only ever populated by a DevScanResultPacket (sent on GUI-open or after Detect) -
        // tagging itself never used to send one, so re-tagging straight from core to a different activation
        // (or vice versa) without an intervening untag left the GUI showing the stale symbol until the next
        // Detect or GUI reopen, even though the in-world glow (driven separately by the BE's own client
        // sync) updated immediately. Sending this after every tag/untag keeps both in step.
        devBlockEntity.getLastScan().ifPresent(scan -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new net.astronomy.multilib.network.DevScanResultPacket(devBlockEntity.getBlockPos(), true, "", scan)));

        switch (outcome) {
            case TAGGED_CORE -> player.sendSystemMessage(Component.literal(
                    "[MultiLib] Tagged " + stateAtPos.getBlock().getName().getString() + " at "
                            + formatPos(clickedPos) + " as the core symbol."));
            case TAGGED_ACTIVATION_DUPLICATE -> player.sendSystemMessage(Component.literal(
                    "[MultiLib] Tagged " + stateAtPos.getBlock().getName().getString() + " at "
                            + formatPos(clickedPos) + " as the activation symbol (too many blocks of this type)."));
            case UNTAGGED -> player.sendSystemMessage(Component.literal(
                    "[MultiLib] Removed the core/activation tag from " + stateAtPos.getBlock().getName().getString() + "."));
            case NOT_PART_OF_SCAN -> player.sendSystemMessage(Component.literal(
                    "[MultiLib] " + stateAtPos.getBlock().getName().getString() + " isn't part of the last scan - run Detect again first."));
            case POSITION_NOT_IN_AREA -> {
                MultiLib.LOGGER.warn(
                        "[MultiLib] MultiblockDevTagHandler: position {} was accepted by the resolver's area "
                                + "check but rejected by the block entity's own area check (dev-block at {}) - "
                                + "areas out of sync.",
                        clickedPos, devBlockEntity.getBlockPos());
                player.sendSystemMessage(Component.literal(
                        "[MultiLib] Internal error: this position isn't part of the dev-block's area. Reopen its GUI and try again."));
            }
        }
    }

    /**
     * Finds the dev-block whose scan area contains {@code clickedPos}. Prefers the player's active tag
     * session (the block whose GUI they last opened) when it's still valid and covers the click, so a
     * player working with two overlapping dev-block areas keeps targeting the one they opened. Otherwise
     * falls back to {@link MultiblockDevBlockIndex} - every loaded dev-block, whether or not this player
     * has an in-memory session for it. That fallback is the whole point: a dev-block persists its area
     * across a relog, but the per-player session (cleared on logout, only rebuilt on GUI-open/Detect)
     * does not, so requiring a session made tagging silently fail after every restart even though the
     * block visibly still had its data. Returns {@code null} only when no loaded dev-block's area covers
     * the click.
     */
    private static MultiblockDevBlockEntity resolveDevBlock(ServerLevel level, ServerPlayer player, BlockPos clickedPos) {
        MultiblockDevTagSessionRegistry.Session session =
                MultiblockDevTagSessionRegistry.get(player.getUUID()).orElse(null);
        if (session != null && isWithinBox(clickedPos, session.boxMin(), session.boxMax())
                && level.getBlockEntity(session.devBlockPos()) instanceof MultiblockDevBlockEntity be) {
            return be;
        }
        for (MultiblockDevBlockIndex.Key key : MultiblockDevBlockIndex.getAll()) {
            if (!key.dimension().equals(level.dimension())) continue;
            if (!(level.getBlockEntity(key.devBlockPos()) instanceof MultiblockDevBlockEntity be)) {
                // Self-heals a stale index entry (block gone without setRemoved firing).
                MultiblockDevBlockIndex.unregister(key.dimension(), key.devBlockPos());
                continue;
            }
            if (be.getAbsoluteBoundingBox().isInside(clickedPos)) return be;
        }
        return null;
    }

    private static boolean isWithinBox(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}

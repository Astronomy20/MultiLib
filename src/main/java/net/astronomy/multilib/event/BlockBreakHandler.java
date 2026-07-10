package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.IMultiblockPart;
import net.astronomy.multilib.api.callback.MultiblockBrokenCallback;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.MultiblockBrokenEvent;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = MultiLib.MODID)
public class BlockBreakHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        Set<MultiblockInstance> instances = tracker.getInstancesAt(pos);
        if (instances.isEmpty()) {
            // TEMP DEBUG - remove once the "hole stays formed" bug is confirmed fixed. If this fires for a
            // position the player clearly just broke off a visibly-formed structure, it means the tracker
            // had no record of that position at all at break time (e.g. a second fast break landing before
            // the first one's deferred retrigger re-registered anything) - the structure is left stale.
            MultiLib.LOGGER.info("[MultiLib DEBUG] onBlockBreak: no tracked instance at broken pos={}", pos);
            return;
        }

        for (MultiblockInstance instance : new HashSet<>(instances)) {
            handleBreak(level, tracker, instance, pos, MultiblockBrokenContext.BreakReason.PLAYER_BREAK);
            // Deferred, NOT called inline here: BlockEvent.BreakEvent fires BEFORE the block at pos is
            // actually removed from the world (see handleBreak's own comment on this same fact) - a
            // match attempt run synchronously right now would still see the old block state at pos and
            // just re-discover the exact same structure unchanged, never a smaller one. Queuing via
            // the server's own task queue runs this after the current tick's synchronous block removal
            // has actually happened, so the retry sees pos as air like it's supposed to.
            level.getServer().execute(() -> retriggerRemainingStructure(level, instance, pos));
        }
    }

    /**
     * For a broken shapeless instance, tries re-forming from whatever's left - e.g. breaking one block
     * off the top of a solid-fill tank should re-form the still-valid smaller structure underneath,
     * not just leave it unformed until something else happens to re-trigger it. Shaped/pattern-provider
     * definitions are deliberately left alone: they have no notion of "still valid at a smaller size"
     * the way shapeless min/maxSize does, so there's nothing meaningful to retry there.
     */
    private static void retriggerRemainingStructure(ServerLevel level, MultiblockInstance brokenInstance, BlockPos removedPos) {
        MultiblockDefinition definition = MultiblockRegistry.get(brokenInstance.getDefinitionId()).orElse(null);
        if (definition == null || !definition.isShapeless() || !definition.hasActivation()) return;

        BlockIngredient activationIngredient = definition.getBlockMap().get(definition.getActivationSymbol());
        if (activationIngredient == null) return;

        // Try EVERY remaining position, not just the first candidate - matchSolidFill's growth is seeded
        // from whatever position it's called with, and that seed must end up inside the final matched
        // bounding box (see its "no valid smaller structure left containing the activation block" case).
        // If we broke one block off the top layer, the OTHER blocks that used to make up that same now-
        // invalid layer are still physically present and still pass activationIngredient.matches() - but
        // seeding the search from one of THEM fails, since the only valid smaller structure left is the
        // layer below, which doesn't contain that seed position. Stopping at the first match (the old
        // behavior) could pick exactly one of those and give up entirely, even though a perfectly valid
        // smaller structure exists and would have matched from a different remaining position (e.g. one
        // already inside the shrunken layer).
        boolean reformed = false;
        for (BlockPos p : brokenInstance.getPositions()) {
            if (p.equals(removedPos) || !level.isLoaded(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!activationIngredient.matches(level, p, state)) continue;
            if (BlockActivationHandler.triggerFormationAt(level, p, null)) {
                reformed = true;
                break;
            }
        }
        // TEMP DEBUG - remove once the "hole stays formed" bug is confirmed fixed.
        MultiLib.LOGGER.info("[MultiLib DEBUG] retriggerRemainingStructure removedPos={} oldSize={} reformed={} "
                        + "newInstancesAtRemovedNeighbors={}", removedPos, brokenInstance.getPositions().size(),
                reformed, describeNeighborInstances(level, removedPos));
    }

    private static String describeNeighborInstances(ServerLevel level, BlockPos removedPos) {
        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos n = removedPos.relative(dir);
            Set<MultiblockInstance> at = tracker.getInstancesAt(n);
            sb.append(dir).append("=").append(at.size());
            for (MultiblockInstance i : at) sb.append("(size=").append(i.getPositions().size()).append(")");
            sb.append(" ");
        }
        return sb.toString();
    }

    public static void handleBreak(ServerLevel level, WorldMultiblockTracker tracker,
                                   MultiblockInstance instance, BlockPos removedPos,
                                   MultiblockBrokenContext.BreakReason reason) {
        tracker.unregister(instance.getId());
        MultiblockContext ctx = MultiblockContext.of(level, instance);

        NeoForge.EVENT_BUS.post(new MultiblockBrokenEvent(ctx, removedPos));

        MultiblockBrokenContext brokenCtx = new MultiblockBrokenContext(ctx, removedPos, reason);
        MultiblockRegistry.get(instance.getDefinitionId()).ifPresent(def -> {
            // Mirrors the true-flip on formation (see BlockActivationHandler#handleFormation) - applied
            // before the broken callbacks below fire, and before the block at removedPos even finishes
            // breaking (BreakEvent hasn't completed the removal yet), so this simply gets overwritten by
            // the actual break right after; harmless, not worth special-casing removedPos out.
            def.getFormedProperty().ifPresent(propertyName -> {
                for (BlockPos pos : instance.getPositions()) {
                    BlockActivationHandler.setFormedPropertyIfPresent(level, pos, propertyName, false);
                }
            });
            for (MultiblockBrokenCallback cb : def.getBrokenCallbacks()) {
                try {
                    cb.onBroken(brokenCtx);
                } catch (Exception e) {
                    MultiLib.LOGGER.error("[MultiLib] onBroken callback for '{}' threw", def.getId(), e);
                }
            }
            BlockActivationHandler.resolveControllerPos(def, instance).ifPresent(controllerPos -> {
                if (level.getBlockEntity(controllerPos) instanceof AbstractMultiblockControllerBE ctrl) {
                    ctrl.onStructureBroken(brokenCtx);
                }
            });
            for (BlockPos pos : instance.getPositions()) {
                if (!level.isLoaded(pos)) continue;
                if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
                    part.getMultiblockComponent().onLeftStructure();
                }
            }
        });
    }

    /**
     * Removes every other block of a structure being explicitly dismantled (e.g. via wrench),
     * honoring each block's {@code dropOriginalOnBreak} flag: blocks that opted in drop normally
     * (preserving NBT/inventory via vanilla's own drop logic), everything else is wiped without
     * drops. NOT called from {@link #onBlockBreak}: breaking a single block of a formed structure
     * only breaks that block, it doesn't collapse the whole structure.
     */
    public static void dismantleRemainingBlocks(ServerLevel level, MultiblockInstance instance, BlockPos removedPos) {
        for (BlockPos pos : instance.getPositions()) {
            if (pos.equals(removedPos) || !level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean dropOriginal = BlockDefinitionRegistry.get(state.getBlock())
                    .map(BlockDefinition::isDropOriginalOnBreak)
                    .orElse(false);
            level.destroyBlock(pos, dropOriginal);
        }
    }
}

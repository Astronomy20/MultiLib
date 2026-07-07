package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.IMultiblockPart;
import net.astronomy.multilib.api.callback.MultiblockBrokenCallback;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.event.MultiblockBrokenEvent;
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
        if (instances.isEmpty()) return;

        for (MultiblockInstance instance : new HashSet<>(instances)) {
            handleBreak(level, tracker, instance, pos, MultiblockBrokenContext.BreakReason.PLAYER_BREAK);
        }
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
            if (def.hasCore()) {
                instance.getCorePos().ifPresent(corePos -> {
                    if (level.getBlockEntity(corePos) instanceof AbstractMultiblockControllerBE ctrl) {
                        ctrl.onStructureBroken(brokenCtx);
                    }
                });
            }
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

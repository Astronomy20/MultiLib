package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockIndex;
import net.astronomy.multilib.core.devtool.MultiblockScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Keeps every dev-block's tag glow and scan contents honest when a block inside its scanned area is
 * broken: previously nothing listened for breaks at all, so breaking the tagged core block left the
 * glow and the core/activation tag active (and the block still listed in the HUD/detect contents)
 * until a manual re-Detect - see {@code MultiblockDevBlockEntity#onAreaBlockBroken}.
 * <p>
 * Everything is deferred by one tick via {@link TickTask}, for two reasons: {@link BlockEvent.BreakEvent}
 * fires <em>before</em> the block is actually removed from the world (an immediate re-scan would still
 * see it), and the event is cancellable - a later listener denying the break would otherwise have made
 * this handler clear a tag for a block that never actually went away. The deferred task re-checks the
 * world state instead of trusting the event: if the position still holds the same block next tick, the
 * break didn't really happen and nothing is touched.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public class MultiblockDevBreakHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos brokenPos = event.getPos();
        BlockState brokenState = event.getState();

        for (MultiblockDevBlockIndex.Key key : MultiblockDevBlockIndex.getAll()) {
            if (!key.dimension().equals(level.dimension())) continue;
            if (!(level.getBlockEntity(key.devBlockPos()) instanceof MultiblockDevBlockEntity be)) {
                // Self-heals a stale entry (block gone without going through setRemoved somehow).
                MultiblockDevBlockIndex.unregister(key.dimension(), key.devBlockPos());
                continue;
            }
            if (!be.getAbsoluteBoundingBox().isInside(brokenPos)) continue;

            BlockPos devBlockPos = key.devBlockPos();
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount() + 1, () -> {
                // The break may have been cancelled by another listener - only act if it landed.
                if (level.getBlockState(brokenPos).getBlock() == brokenState.getBlock()) return;
                if (!(level.getBlockEntity(devBlockPos) instanceof MultiblockDevBlockEntity current)) return;

                current.onAreaBlockBroken(brokenPos, brokenState);

                // Refresh the scan so the HUD list/export contents drop the broken block, exactly like
                // the auto-detect tick path - including sending failed scans, so a watching player's
                // HUD clears instead of showing stale content (e.g. the area just became empty).
                if (current.getLastScan().isEmpty()) return;
                MultiblockScanner.ScanOutcome outcome = current.detectAndStore();
                for (ServerPlayer player : level.players()) {
                    MultiblockDevPacketHandler.sendScanIfWatching(player, level.dimension(), devBlockPos, outcome);
                }
            }));
        }
    }
}

package net.astronomy.multilib.event;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockDevAutoDetectRegistry;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity;
import net.astronomy.multilib.core.devtool.MultiblockScanner;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = MultiLib.MODID)
public class MultiblockTickHandler {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WorldMultiblockTracker.get(level).tick(level);
        tickDevAutoDetect(level);
    }

    /**
     * Re-scans every dev-block with auto-detect switched on, not just whichever one(s) a player happens
     * to be watching via the HUD list - a dev-block's {@code lastScan} (and therefore the core/activation
     * tag glow, which is re-derived from it) should stay fresh in the background regardless of whether
     * its list is currently shown, since Detect and "Show/Hide List" are two independent toggles. Keyed
     * off the level's own game time rather than a manually incremented counter: a static counter shared
     * across every dimension's separate {@code LevelTickEvent.Post} firing would advance 2-3x too fast
     * with multiple dimensions loaded (Overworld/Nether/End each tick it once), where {@code getGameTime()}
     * is already correctly per-dimension.
     */
    private static void tickDevAutoDetect(ServerLevel level) {
        if (level.getGameTime() % CommonConfig.DEVTOOL_AUTO_DETECT_INTERVAL_TICKS.get() != 0) return;

        for (MultiblockDevAutoDetectRegistry.Key key : MultiblockDevAutoDetectRegistry.getAll()) {
            if (!key.dimension().equals(level.dimension())) continue;

            if (!(level.getBlockEntity(key.devBlockPos()) instanceof MultiblockDevBlockEntity be) || !be.isAutoDetectOn()) {
                // Self-heals a stale entry (block broken/replaced without going through the normal
                // unregister path, or auto-detect was turned off through some path that missed it).
                MultiblockDevAutoDetectRegistry.unregister(key.dimension(), key.devBlockPos());
                continue;
            }

            MultiblockScanner.ScanOutcome outcome = be.detectAndStore();

            // Only actually sent over the network to whichever player (if any) currently has this exact
            // block's HUD list shown - the periodic re-scan itself always happens (freshness for the tag
            // glow/export benefits everyone), but nobody needs the packet if nobody's watching. Sent
            // regardless of whether the scan succeeded: previously a failed re-scan (e.g. the area became
            // empty because the last block in it was broken) was silently dropped instead of telling the
            // watching player's HUD to clear the stale content it was still showing.
            for (ServerPlayer player : level.players()) {
                MultiblockDevPacketHandler.sendScanIfWatching(player, level.dimension(), key.devBlockPos(), outcome);
            }
        }
    }
}

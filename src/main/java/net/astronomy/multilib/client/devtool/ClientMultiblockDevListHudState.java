package net.astronomy.multilib.client.devtool;

import net.astronomy.multilib.core.devtool.MultiblockScanResult;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of "which dev-block's scoreboard-style HUD list (if any) is currently shown",
 * populated by {@link DevListVisibilityPacket}, and the scan content itself piggybacked on whatever
 * {@code DevScanResultPacket} happens to already be flowing for that block (Detect, a wrench tag, or
 * the periodic auto-detect tick) - see {@code ClientPacketHandler#handleDevScanResult}.
 * <p>
 * Single active target, matching {@code MultiblockDevListSessionRegistry}'s single-slot design
 * server-side: activating a different dev-block's list is a plain overwrite here, not something that
 * needs the old target to be told separately that it was replaced.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMultiblockDevListHudState {

    private ClientMultiblockDevListHudState() {
    }

    private static @Nullable BlockPos activePos;
    private static @Nullable MultiblockScanResult scan;

    /** @param pos the newly active dev-block, or {@code null} to hide the HUD entirely. */
    public static void setActive(@Nullable BlockPos pos) {
        activePos = pos;
        scan = null;
    }

    /** No-ops unless {@code pos} is the currently active target - a scan result for some other dev-block shouldn't touch this HUD's content. */
    public static void updateIfActive(BlockPos pos, @Nullable MultiblockScanResult newScan) {
        if (activePos != null && activePos.equals(pos)) {
            scan = newScan;
        }
    }

    public static @Nullable BlockPos getActivePos() {
        return activePos;
    }

    public static @Nullable MultiblockScanResult getScan() {
        return scan;
    }
}

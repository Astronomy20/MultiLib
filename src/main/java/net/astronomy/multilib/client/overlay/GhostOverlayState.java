package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class GhostOverlayState {

    public static final GhostOverlayState INSTANCE = new GhostOverlayState();

    private List<GhostBlockData> blocks = new ArrayList<>();
    private int activeMode = -1;
    private boolean debugTiming = false;

    private GhostOverlayState() {}

    /**
     * Called on every OverlayDataPacket received from the server. The server is the sole authority
     * on when the overlay is active: it manages the session timer and sends activeMode=-1 when the
     * duration expires. No client-side timer is needed — and adding one caused a race condition
     * where isActive() set activeMode=-1 locally, then the server's next refresh packet (with
     * activeMode>=0) was incorrectly treated as a fresh activation, resetting the timer indefinitely.
     */
    public void update(OverlayDataPacket packet) {
        this.blocks = new ArrayList<>(packet.blocks());
        this.activeMode = packet.activeMode();
        this.debugTiming = packet.debugTiming();
    }

    /** Dev-only: whether the active structure was built with .ghostOverlayDebug() on its definition. */
    public boolean isDebugTiming() {
        return debugTiming;
    }

    public boolean isActive() {
        return activeMode >= 0;
    }

    public List<GhostBlockData> getBlocksToRender() {
        if (!isActive()) return List.of();
        return blocks;
    }

    public void disable() {
        activeMode = -1;
    }
}

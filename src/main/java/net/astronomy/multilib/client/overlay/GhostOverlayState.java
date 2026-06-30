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

    private static final long TIMEOUT_MS = 30_000L;

    private List<GhostBlockData> blocks = new ArrayList<>();
    private int activeMode = -1;
    private long lastInteractionTime = 0;
    private boolean debugTiming = false;

    private GhostOverlayState() {}

    public void update(OverlayDataPacket packet) {
        this.blocks = new ArrayList<>(packet.blocks());
        this.activeMode = packet.activeMode();
        this.lastInteractionTime = System.currentTimeMillis();
        this.debugTiming = packet.debugTiming();
    }

    /** Dev-only: whether the active structure was built with .ghostOverlayDebug() on its definition. */
    public boolean isDebugTiming() {
        return debugTiming;
    }

    public boolean isActive() {
        if (activeMode < 0) return false;
        if (System.currentTimeMillis() - lastInteractionTime > TIMEOUT_MS) {
            activeMode = -1;
            return false;
        }
        return true;
    }

    public List<GhostBlockData> getBlocksToRender() {
        if (!isActive()) return List.of();
        return blocks;
    }

    public void disable() {
        activeMode = -1;
    }
}

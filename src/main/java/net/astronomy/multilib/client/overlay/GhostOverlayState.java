package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
    // -1 = "not yet printed for this activation", so the very first packet always prints.
    private int lastPrintedRemainingSeconds = -1;

    private GhostOverlayState() {}

    /**
     * Called on every OverlayDataPacket received from the server. The server is the sole authority
     * on when the overlay is active: it manages the session timer and sends activeMode=-1 when the
     * duration expires. No client-side timer is needed - and adding one caused a race condition
     * where isActive() set activeMode=-1 locally, then the server's next refresh packet (with
     * activeMode>=0) was incorrectly treated as a fresh activation, resetting the timer indefinitely.
     * <p>
     * The debug countdown message (see {@link #maybePrintCountdown}) is driven from here rather than
     * from the render loop for the same reason the timer itself is server-authoritative: this method
     * only runs when a packet actually arrives, and the server stops sending packets while its own
     * tick loop is frozen (e.g. the integrated server pausing in single player) - so the message
     * naturally stops right along with the timer instead of continuing on a client-side wall clock.
     */
    public void update(OverlayDataPacket packet) {
        this.blocks = new ArrayList<>(packet.blocks());
        this.activeMode = packet.activeMode();
        this.debugTiming = packet.debugTiming();
        if (!isActive()) {
            lastPrintedRemainingSeconds = -1;
            return;
        }
        maybePrintCountdown(packet.remainingSeconds());
    }

    private void maybePrintCountdown(int remainingSeconds) {
        if (!debugTiming || remainingSeconds == lastPrintedRemainingSeconds) return;
        lastPrintedRemainingSeconds = remainingSeconds;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // -1 is OverlayRequestHandler's "never expires" sentinel (MultiblockBuilder#ghostOverlayPersistent()).
        String message = remainingSeconds < 0
                ? "[MultiLib debug] Ghost overlay is persistent (won't expire)"
                : "[MultiLib debug] Ghost overlay expires in " + remainingSeconds + "s";
        mc.player.displayClientMessage(Component.literal(message), false);
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

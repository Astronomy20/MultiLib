package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.GhostBlockData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** Holds the latest auto-place preview (positions the held item can fill) received from the server. */
@OnlyIn(Dist.CLIENT)
public class AutoPlacePreviewState {

    public static final AutoPlacePreviewState INSTANCE = new AutoPlacePreviewState();

    private List<GhostBlockData> blocks = new ArrayList<>();

    private AutoPlacePreviewState() {}

    public void update(AutoPlacePreviewDataPacket packet) {
        this.blocks = new ArrayList<>(packet.blocks());
    }

    public List<GhostBlockData> getBlocksToRender() {
        return blocks;
    }

    public void clear() {
        if (!blocks.isEmpty()) blocks = new ArrayList<>();
    }
}

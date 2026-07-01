package net.astronomy.multilib.client.network;

import net.astronomy.multilib.client.overlay.AutoPlacePreviewState;
import net.astronomy.multilib.client.overlay.GhostOverlayState;
import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOverlayData(OverlayDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GhostOverlayState.INSTANCE.update(packet));
    }

    public static void handleAutoPlacePreviewData(AutoPlacePreviewDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AutoPlacePreviewState.INSTANCE.update(packet));
    }
}

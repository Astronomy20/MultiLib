package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Sent server → client with ghost block positions for the overlay.
 * activeMode: -1 = disabled, 0 = full render, N >= 1 = show only layer N
 */
public record OverlayDataPacket(List<GhostBlockData> blocks, int totalLayers, int activeMode, boolean debugTiming)
        implements CustomPacketPayload {

    public static final Type<OverlayDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "overlay_data"));

    public static final StreamCodec<ByteBuf, OverlayDataPacket> STREAM_CODEC = StreamCodec.composite(
        GhostBlockData.STREAM_CODEC.apply(ByteBufCodecs.list()), OverlayDataPacket::blocks,
        ByteBufCodecs.VAR_INT, OverlayDataPacket::totalLayers,
        ByteBufCodecs.VAR_INT, OverlayDataPacket::activeMode,
        ByteBufCodecs.BOOL, OverlayDataPacket::debugTiming,
        OverlayDataPacket::new
    );

    @Override
    public Type<OverlayDataPacket> type() {
        return TYPE;
    }
}

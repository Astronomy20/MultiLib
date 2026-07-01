package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Sent server -> client with the (possibly empty) set of missing positions the player's held item
 * can currently fill via auto-place, ghost-overlay style. An empty list clears the preview.
 */
public record AutoPlacePreviewDataPacket(List<GhostBlockData> blocks) implements CustomPacketPayload {

    public static final Type<AutoPlacePreviewDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "autoplace_preview_data"));

    public static final StreamCodec<ByteBuf, AutoPlacePreviewDataPacket> STREAM_CODEC = StreamCodec.composite(
        GhostBlockData.STREAM_CODEC.apply(ByteBufCodecs.list()), AutoPlacePreviewDataPacket::blocks,
        AutoPlacePreviewDataPacket::new
    );

    @Override
    public Type<AutoPlacePreviewDataPacket> type() {
        return TYPE;
    }
}

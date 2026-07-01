package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server while the player looks at an auto-place-overlay-enabled core block of an
 * unformed structure, asking which missing positions the player's currently held item could fill.
 * Unlike {@link RequestAutoPlacePacket}, this doesn't place anything — it's re-sent periodically
 * while hovering (see the client-side hover handler) so the preview stays in sync with inventory
 * and world changes, and a corePos of {@link BlockPos#ZERO} with {@code active=false} clears it.
 */
public record RequestAutoPlacePreviewPacket(BlockPos corePos, boolean active) implements CustomPacketPayload {

    public static final Type<RequestAutoPlacePreviewPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_autoplace_preview"));

    public static final StreamCodec<ByteBuf, RequestAutoPlacePreviewPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestAutoPlacePreviewPacket::corePos,
        ByteBufCodecs.BOOL, RequestAutoPlacePreviewPacket::active,
        RequestAutoPlacePreviewPacket::new
    );

    @Override
    public Type<RequestAutoPlacePreviewPacket> type() {
        return TYPE;
    }
}

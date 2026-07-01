package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the player Ctrl+Right-clicks a core block, requesting an auto-place
 * attempt for that multiblock's pattern. When the auto-place overlay was showing at click time,
 * {@code hasOverlayTarget} is {@code true} and {@code overlayTargetPos} carries the exact position
 * the overlay displayed, so the server places there instead of recomputing a (possibly different)
 * position from the player's current facing.
 */
public record RequestAutoPlacePacket(BlockPos corePos, BlockPos overlayTargetPos, boolean hasOverlayTarget)
        implements CustomPacketPayload {

    public static final Type<RequestAutoPlacePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_autoplace"));

    public static final StreamCodec<ByteBuf, RequestAutoPlacePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestAutoPlacePacket::corePos,
        BlockPos.STREAM_CODEC, RequestAutoPlacePacket::overlayTargetPos,
        ByteBufCodecs.BOOL, RequestAutoPlacePacket::hasOverlayTarget,
        RequestAutoPlacePacket::new
    );

    @Override
    public Type<RequestAutoPlacePacket> type() {
        return TYPE;
    }
}

package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the player Ctrl+Right-clicks a core block, requesting an auto-place
 * attempt for that multiblock's pattern.
 */
public record RequestAutoPlacePacket(BlockPos corePos) implements CustomPacketPayload {

    public static final Type<RequestAutoPlacePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_autoplace"));

    public static final StreamCodec<ByteBuf, RequestAutoPlacePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestAutoPlacePacket::corePos,
        RequestAutoPlacePacket::new
    );

    @Override
    public Type<RequestAutoPlacePacket> type() {
        return TYPE;
    }
}

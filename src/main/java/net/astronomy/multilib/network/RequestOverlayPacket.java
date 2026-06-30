package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client → server to request or advance the ghost overlay for a multiblock.
 * mode: 0 = advance cycle (next state), -1 = disable
 * faceOrdinal: {@link Direction#ordinal()} of the core face that was shift-clicked, or -1 if unknown/
 * not applicable — lets the overlay preview a different permitted rotation depending on which face
 * was clicked, for definitions that allow more than the default orientation.
 */
public record RequestOverlayPacket(BlockPos corePos, int mode, int faceOrdinal) implements CustomPacketPayload {

    public static final Type<RequestOverlayPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_overlay"));

    public static final StreamCodec<ByteBuf, RequestOverlayPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestOverlayPacket::corePos,
        ByteBufCodecs.VAR_INT, RequestOverlayPacket::mode,
        ByteBufCodecs.VAR_INT, RequestOverlayPacket::faceOrdinal,
        RequestOverlayPacket::new
    );

    @Override
    public Type<RequestOverlayPacket> type() {
        return TYPE;
    }
}

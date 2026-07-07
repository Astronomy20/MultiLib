package net.astronomy.multilib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the "Detect" button (now an on/off toggle rather than a one-shot action) is
 * clicked - flips {@code MultiblockDevBlockEntity#isAutoDetectOn()}. See
 * {@code MultiblockTickHandler} for the periodic re-scan this enables, and
 * {@code MultiblockDevPacketHandler#handleAutoDetectToggleRequest} for the handler.
 */
public record RequestDevAutoDetectTogglePacket(BlockPos devBlockPos) implements CustomPacketPayload {

    public static final Type<RequestDevAutoDetectTogglePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_auto_detect_toggle"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestDevAutoDetectTogglePacket> STREAM_CODEC =
        BlockPos.STREAM_CODEC.map(RequestDevAutoDetectTogglePacket::new, RequestDevAutoDetectTogglePacket::devBlockPos);

    @Override
    public Type<RequestDevAutoDetectTogglePacket> type() {
        return TYPE;
    }
}

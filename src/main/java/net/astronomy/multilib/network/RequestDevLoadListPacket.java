package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the Multiblock Dev Block GUI's Load tab is opened, asking for the list of
 * JSON exports already written under {@code MultiblockDevOutputPaths.jsonRootDir} - see
 * {@link net.astronomy.multilib.core.devtool.MultiblockDevExportLoader#list}. Answered with
 * {@link DevLoadListPacket}.
 */
public record RequestDevLoadListPacket(BlockPos devBlockPos) implements CustomPacketPayload {

    public static final Type<RequestDevLoadListPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_load_list"));

    public static final StreamCodec<ByteBuf, RequestDevLoadListPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestDevLoadListPacket::devBlockPos,
        RequestDevLoadListPacket::new
    );

    @Override
    public Type<RequestDevLoadListPacket> type() {
        return TYPE;
    }
}

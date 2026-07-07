package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server to (re)configure and scan the area on the Multiblock Dev Block at
 * {@code devBlockPos}. Carries the full GUI field set (offset/size/path/displayName) so a
 * single click on Detect both applies whatever the developer just typed and immediately re-scans with
 * it - there is no separate "save fields" packet. See
 * {@code MultiblockDevBlockEntity#detectAndStore()} for the actual scan logic and
 * {@code MultiblockDevPacketHandler#handleScanRequest} for the handler.
 * <p>
 * No {@code namespace} field: the export id's namespace half is always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
 * a single fixed value with no GUI field of its own - see {@code MultiblockDevBlockEntity}'s own former
 * {@code namespace} field, removed for the same reason.
 * <p>
 * Hand-written {@link StreamCodec} (via {@link FriendlyByteBuf} directly) rather than
 * {@code StreamCodec.composite(...)}: this record has 7 fields, more than composite's supported arity.
 */
public record RequestDevScanPacket(BlockPos devBlockPos, BlockPos offset, int sizeX, int sizeY, int sizeZ,
                                    String path, String displayName)
        implements CustomPacketPayload {

    public static final Type<RequestDevScanPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_scan"));

    public static final StreamCodec<FriendlyByteBuf, RequestDevScanPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeBlockPos(packet.devBlockPos());
            buf.writeBlockPos(packet.offset());
            buf.writeVarInt(packet.sizeX());
            buf.writeVarInt(packet.sizeY());
            buf.writeVarInt(packet.sizeZ());
            buf.writeUtf(packet.path());
            buf.writeUtf(packet.displayName());
        },
        buf -> new RequestDevScanPacket(
            buf.readBlockPos(),
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readUtf(),
            buf.readUtf()
        )
    );

    @Override
    public Type<RequestDevScanPacket> type() {
        return TYPE;
    }
}

package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the Multiblock Dev Block GUI closes, so whatever's currently typed (even
 * if the developer never clicked Detect) survives closing and reopening the GUI. Same field set as
 * {@link RequestDevScanPacket}, but the handler only applies the setters - it does not scan, and
 * critically does not clear the current tag the way {@code detectAndStore()} does. Without this, closing
 * the GUI without clicking Detect first silently discarded any typed-but-unsubmitted edits, since nothing
 * had told the block entity about them yet.
 * <p>
 * No {@code namespace} field: the export id's namespace half is always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
 * a single fixed value with no GUI field of its own - see {@code MultiblockDevBlockEntity}'s own former
 * {@code namespace} field, removed for the same reason.
 */
public record RequestDevSaveFieldsPacket(BlockPos devBlockPos, BlockPos offset, int sizeX, int sizeY, int sizeZ,
                                          String path, String displayName, String variantName)
        implements CustomPacketPayload {

    public static final Type<RequestDevSaveFieldsPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_save_fields"));

    public static final StreamCodec<FriendlyByteBuf, RequestDevSaveFieldsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeBlockPos(packet.devBlockPos());
            buf.writeBlockPos(packet.offset());
            buf.writeVarInt(packet.sizeX());
            buf.writeVarInt(packet.sizeY());
            buf.writeVarInt(packet.sizeZ());
            buf.writeUtf(packet.path());
            buf.writeUtf(packet.displayName());
            buf.writeUtf(packet.variantName());
        },
        buf -> new RequestDevSaveFieldsPacket(
            buf.readBlockPos(),
            buf.readBlockPos(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf()
        )
    );

    @Override
    public Type<RequestDevSaveFieldsPacket> type() {
        return TYPE;
    }
}

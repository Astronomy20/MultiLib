package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server to export the last scan stored on the Multiblock Dev Block at
 * {@code devBlockPos} in the requested {@link Format} (copy-to-clipboard + write-to-file, both
 * server-side - see {@code MultiblockDevPacketHandler#handleExportRequest}).
 * <p>
 * {@code force}: normally {@code false} - if the namespace is already used by a *different* multiblock's
 * export, the server refuses the write and instead answers with a
 * {@link DevExportResultPacket#requiresConfirmation()} response, which the GUI turns into a confirmation
 * popup (see {@code MultiblockDevScreen}). Re-sent with {@code force=true} only after the developer
 * explicitly confirms overwriting it.
 */
public record RequestDevExportPacket(BlockPos devBlockPos, Format format, boolean force) implements CustomPacketPayload {

    public enum Format { JAVA, JSON, KUBEJS }

    public static final Type<RequestDevExportPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_export"));

    public static final StreamCodec<ByteBuf, Format> FORMAT_CODEC =
        ByteBufCodecs.VAR_INT.map(i -> Format.values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, RequestDevExportPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestDevExportPacket::devBlockPos,
        FORMAT_CODEC, RequestDevExportPacket::format,
        ByteBufCodecs.BOOL, RequestDevExportPacket::force,
        RequestDevExportPacket::new
    );

    @Override
    public Type<RequestDevExportPacket> type() {
        return TYPE;
    }
}

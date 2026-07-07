package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.SourceFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the developer picks one entry in the Load tab's list - asks the server to
 * re-read that specific multiblock (identified by {@code format}/{@code namespace}/{@code path}) and
 * apply it to the dev block (path/display-name/scan), so it can be edited. {@code namespace} is needed
 * explicitly now: the Load tab lists every currently *registered* multiblock too (hardcoded Java from any
 * mod, JSON datapacks, KubeJS - see {@code MultiblockDevExportLoader#listFromRegistry}), not just this
 * dev tool's own exports, so the namespace half can no longer be assumed to always be
 * {@code CommonConfig.DEVTOOL_NAMESPACE}. Answered with {@link DevLoadResultPacket}.
 */
public record RequestDevLoadPacket(BlockPos devBlockPos, SourceFormat format, String namespace, String path) implements CustomPacketPayload {

    public static final Type<RequestDevLoadPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_load"));

    public static final StreamCodec<ByteBuf, RequestDevLoadPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestDevLoadPacket::devBlockPos,
        DevLoadListPacket.FORMAT_CODEC, RequestDevLoadPacket::format,
        ByteBufCodecs.STRING_UTF8, RequestDevLoadPacket::namespace,
        ByteBufCodecs.STRING_UTF8, RequestDevLoadPacket::path,
        RequestDevLoadPacket::new
    );

    @Override
    public Type<RequestDevLoadPacket> type() {
        return TYPE;
    }
}

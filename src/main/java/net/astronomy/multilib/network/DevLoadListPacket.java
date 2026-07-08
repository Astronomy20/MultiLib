package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.LoadableMultiblock;
import net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.SourceFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Sent server -> client in answer to {@link RequestDevLoadListPacket}, with every export currently found (all three formats). */
public record DevLoadListPacket(BlockPos devBlockPos, List<LoadableMultiblock> entries) implements CustomPacketPayload {

    public static final Type<DevLoadListPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "dev_load_list"));

    public static final StreamCodec<ByteBuf, SourceFormat> FORMAT_CODEC =
        ByteBufCodecs.VAR_INT.map(i -> SourceFormat.values()[i], SourceFormat::ordinal);

    private static final StreamCodec<ByteBuf, LoadableMultiblock> ENTRY_CODEC = StreamCodec.composite(
        FORMAT_CODEC, LoadableMultiblock::format,
        ByteBufCodecs.STRING_UTF8, LoadableMultiblock::namespace,
        ByteBufCodecs.STRING_UTF8, LoadableMultiblock::path,
        ByteBufCodecs.STRING_UTF8, LoadableMultiblock::displayName,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), LoadableMultiblock::variantNames,
        LoadableMultiblock::new
    );

    public static final StreamCodec<ByteBuf, DevLoadListPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DevLoadListPacket::devBlockPos,
        ENTRY_CODEC.apply(ByteBufCodecs.list()), DevLoadListPacket::entries,
        DevLoadListPacket::new
    );

    @Override
    public Type<DevLoadListPacket> type() {
        return TYPE;
    }
}

package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client → server by the preference wrench (see {@code core.preference}) once a player picks one
 * of the candidate definitions shown for an ambiguous core/activation block - binds {@code definitionId}
 * to {@code pos} via {@code MultiLib#setPreferredDefinition}. Answered by
 * {@link PreferredDefinitionResultPacket}.
 */
public record RequestSetPreferredDefinitionPacket(BlockPos pos, ResourceLocation definitionId) implements CustomPacketPayload {

    public static final Type<RequestSetPreferredDefinitionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_set_preferred_definition"));

    public static final StreamCodec<ByteBuf, RequestSetPreferredDefinitionPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RequestSetPreferredDefinitionPacket::pos,
        ResourceLocation.STREAM_CODEC, RequestSetPreferredDefinitionPacket::definitionId,
        RequestSetPreferredDefinitionPacket::new
    );

    @Override
    public Type<RequestSetPreferredDefinitionPacket> type() {
        return TYPE;
    }
}

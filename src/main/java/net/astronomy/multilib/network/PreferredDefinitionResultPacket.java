package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server → client in response to {@link RequestSetPreferredDefinitionPacket}. {@code success} is
 * {@code false} only when {@code definitionId} wasn't actually a valid core/activation candidate for
 * the block at {@code pos} by the time the server processed the request (see
 * {@code MultiLibAPI#setPreferredDefinition}'s validation) - e.g. the block changed between the client
 * opening the picker and clicking a choice. The client-side handler shows this as a simple chat line;
 * the preference wrench (and this whole feature) only ever exists when {@code CommonConfig.DEV_MODE}
 * was on at registration time, so no separate dev-mode gate is needed for the feedback itself.
 */
public record PreferredDefinitionResultPacket(BlockPos pos, boolean success, ResourceLocation definitionId) implements CustomPacketPayload {

    public static final Type<PreferredDefinitionResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "preferred_definition_result"));

    public static final StreamCodec<ByteBuf, PreferredDefinitionResultPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PreferredDefinitionResultPacket::pos,
        ByteBufCodecs.BOOL, PreferredDefinitionResultPacket::success,
        ResourceLocation.STREAM_CODEC, PreferredDefinitionResultPacket::definitionId,
        PreferredDefinitionResultPacket::new
    );

    @Override
    public Type<PreferredDefinitionResultPacket> type() {
        return TYPE;
    }
}

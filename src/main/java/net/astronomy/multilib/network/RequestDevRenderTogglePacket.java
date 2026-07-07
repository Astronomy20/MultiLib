package net.astronomy.multilib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the "Render" button is clicked - flips
 * {@code MultiblockDevBlockEntity#isRenderOn()}, persisting the area-preview toggle (unlike the GUI
 * Screen's own local {@code previewOn}) so it survives closing the GUI, relogging, or a world restart -
 * see {@code MultiblockDevBlockEntity#propagateRenderPreviewIfApplicable}.
 */
public record RequestDevRenderTogglePacket(BlockPos devBlockPos) implements CustomPacketPayload {

    public static final Type<RequestDevRenderTogglePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_render_toggle"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestDevRenderTogglePacket> STREAM_CODEC =
        BlockPos.STREAM_CODEC.map(RequestDevRenderTogglePacket::new, RequestDevRenderTogglePacket::devBlockPos);

    @Override
    public Type<RequestDevRenderTogglePacket> type() {
        return TYPE;
    }
}

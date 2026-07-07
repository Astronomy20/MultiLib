package net.astronomy.multilib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent client -> server when the GUI's "Show/Hide List" button (between Detect and Render) is clicked -
 * toggles whether this player currently has {@code devBlockPos}'s scoreboard-style HUD list shown, via
 * {@code MultiblockDevListSessionRegistry#toggle}. See
 * {@code MultiblockDevPacketHandler#handleListToggleRequest} for the handler and
 * {@link DevListVisibilityPacket} for the resulting client-side update.
 */
public record RequestDevListToggleVisibilityPacket(BlockPos devBlockPos) implements CustomPacketPayload {

    public static final Type<RequestDevListToggleVisibilityPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "request_dev_list_toggle_visibility"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RequestDevListToggleVisibilityPacket> STREAM_CODEC =
        BlockPos.STREAM_CODEC.map(RequestDevListToggleVisibilityPacket::new, RequestDevListToggleVisibilityPacket::devBlockPos);

    @Override
    public Type<RequestDevListToggleVisibilityPacket> type() {
        return TYPE;
    }
}

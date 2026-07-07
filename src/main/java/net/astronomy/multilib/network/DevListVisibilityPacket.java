package net.astronomy.multilib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server -> client to tell it which dev-block's HUD list (if any) to show. {@code visible=false}
 * hides whatever's currently shown outright (client ignores {@code devBlockPos} in that case - see
 * {@code ClientMultiblockDevListHudState#setActive}). Content for the list itself piggybacks on the
 * already-existing {@link DevScanResultPacket} rather than a dedicated payload - see
 * {@code ClientPacketHandler#handleDevScanResult}, which feeds both the GUI's menu (if open) and this
 * HUD state (if the incoming scan's position matches the currently active one).
 */
public record DevListVisibilityPacket(boolean visible, BlockPos devBlockPos) implements CustomPacketPayload {

    public static final Type<DevListVisibilityPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "dev_list_visibility"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, DevListVisibilityPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, DevListVisibilityPacket::visible,
        BlockPos.STREAM_CODEC, DevListVisibilityPacket::devBlockPos,
        DevListVisibilityPacket::new
    );

    @Override
    public Type<DevListVisibilityPacket> type() {
        return TYPE;
    }
}

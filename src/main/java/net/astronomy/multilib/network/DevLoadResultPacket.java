package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.astronomy.multilib.core.devtool.MultiblockScanResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Sent server -> client with the outcome of a {@link RequestDevLoadPacket}. On success, carries the
 * loaded multiblock's path/display-name and reconstructed {@link MultiblockScanResult} so the
 * GUI can populate its fields and scan summary exactly as if the developer had typed them and scanned
 * that structure themselves. Reuses {@link DevScanResultPacket#SCAN_RESULT_CODEC} - same payload shape,
 * no reason to duplicate it.
 * <p>
 * No {@code namespace} field: the export id's namespace half is always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
 * a single fixed value the GUI never edits - carrying a per-load copy of it back to the client had no
 * consumer once the GUI stopped pretending it was an editable/derived field (see
 * {@code MultiblockDevBlockEntity}'s own former {@code namespace} field, removed for the same reason).
 */
public record DevLoadResultPacket(BlockPos devBlockPos, boolean success, String message,
                                   String path, String displayName,
                                   MultiblockScanResult scan) implements CustomPacketPayload {

    public static final Type<DevLoadResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "dev_load_result"));

    private static final MultiblockScanResult EMPTY_SCAN =
        new MultiblockScanResult(List.of(), new LinkedHashMap<>(), null, null);

    /** path/displayName bundled into one sub-codec - {@code StreamCodec.composite} tops out at 6 fields, and the top-level packet already needs 4 slots without these two squeezed into one. */
    private record Identity(String path, String displayName) {}

    private static final StreamCodec<ByteBuf, Identity> IDENTITY_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Identity::path,
        ByteBufCodecs.STRING_UTF8, Identity::displayName,
        Identity::new
    );

    public static final StreamCodec<ByteBuf, DevLoadResultPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DevLoadResultPacket::devBlockPos,
        ByteBufCodecs.BOOL, DevLoadResultPacket::success,
        ByteBufCodecs.STRING_UTF8, DevLoadResultPacket::message,
        IDENTITY_CODEC, p -> new Identity(p.path(), p.displayName()),
        DevScanResultPacket.SCAN_RESULT_CODEC, p -> p.scan() != null ? p.scan() : EMPTY_SCAN,
        (devBlockPos, success, message, identity, scan) ->
            new DevLoadResultPacket(devBlockPos, success, message, identity.path(), identity.displayName(), scan)
    );

    @Override
    public Type<DevLoadResultPacket> type() {
        return TYPE;
    }
}

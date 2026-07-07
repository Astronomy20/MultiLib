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
import java.util.Map;

/**
 * Sent server -> client with the outcome of a {@link RequestDevScanPacket}. On success, {@code scan}
 * carries the full {@link MultiblockScanResult}; on failure {@code scan} is {@code null} and
 * {@code message} explains why (e.g. too many distinct block types, dev-block position ended up inside
 * its own scan area).
 * <p>
 * {@link MultiblockScanResult} has no {@code StreamCodec} of its own (it's a plain record owned by
 * another part of this feature) - encoding/decoding it lives here since this packet is the only place
 * that needs to move one across the network.
 */
public record DevScanResultPacket(BlockPos devBlockPos, boolean success, String message,
                                   MultiblockScanResult scan) implements CustomPacketPayload {

    public static final Type<DevScanResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "dev_scan_result"));

    private static final StreamCodec<ByteBuf, List<String>> LAYER_ROWS_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, List<List<String>>> LAYERS_CODEC =
        LAYER_ROWS_CODEC.apply(ByteBufCodecs.list());

    // Symbol/block map is transmitted as two parallel lists (chars-as-strings + ResourceLocations)
    // instead of a map codec, then rebuilt into a LinkedHashMap on decode to preserve insertion order
    // (determinism matters for re-export, per the roadmap).
    private static final StreamCodec<ByteBuf, List<String>> SYMBOLS_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, List<ResourceLocation>> BLOCK_IDS_CODEC =
        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    // Nullable Character encoded as a single-char string, empty string = absent.
    private static String charToString(Character c) {
        return c == null ? "" : String.valueOf((char) c);
    }

    private static Character stringToChar(String s) {
        return s.isEmpty() ? null : s.charAt(0);
    }

    public static final StreamCodec<ByteBuf, MultiblockScanResult> SCAN_RESULT_CODEC = StreamCodec.composite(
        LAYERS_CODEC, MultiblockScanResult::layers,
        SYMBOLS_CODEC, r -> r.symbolToBlock().keySet().stream().map(String::valueOf).toList(),
        BLOCK_IDS_CODEC, r -> List.copyOf(r.symbolToBlock().values()),
        ByteBufCodecs.STRING_UTF8, r -> charToString(r.coreSymbol()),
        ByteBufCodecs.STRING_UTF8, r -> charToString(r.activationSymbol()),
        (layers, symbols, blockIds, coreStr, activationStr) -> {
            LinkedHashMap<Character, ResourceLocation> map = new LinkedHashMap<>();
            for (int i = 0; i < symbols.size() && i < blockIds.size(); i++) {
                map.put(symbols.get(i).charAt(0), blockIds.get(i));
            }
            return new MultiblockScanResult(layers, map, stringToChar(coreStr), stringToChar(activationStr));
        }
    );

    private static final MultiblockScanResult EMPTY_SCAN =
        new MultiblockScanResult(List.of(), new LinkedHashMap<>(), null, null);

    public static final StreamCodec<ByteBuf, DevScanResultPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DevScanResultPacket::devBlockPos,
        ByteBufCodecs.BOOL, DevScanResultPacket::success,
        ByteBufCodecs.STRING_UTF8, DevScanResultPacket::message,
        SCAN_RESULT_CODEC, p -> p.scan() != null ? p.scan() : EMPTY_SCAN,
        DevScanResultPacket::new
    );

    @Override
    public Type<DevScanResultPacket> type() {
        return TYPE;
    }
}

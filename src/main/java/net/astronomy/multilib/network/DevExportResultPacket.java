package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent server -> client with the outcome of a {@link RequestDevExportPacket}. {@code generatedText} is
 * always populated (even when {@code writeSucceeded} is false) so the client can still copy it to the
 * clipboard - a local file-write failure (permissions, missing path, ...) must never cost the developer
 * the generated text itself (see roadmap Design 5 / "isolamento del fallimento di scrittura file dal
 * copy-to-clipboard").
 * <p>
 * {@code requiresConfirmation}: true only for the specific "this namespace is already used by a
 * different multiblock" refusal - never written in this state, {@code writeSucceeded} is false and
 * nothing touched the filesystem. The GUI turns this into a confirmation popup and, if confirmed,
 * re-sends {@link RequestDevExportPacket} for the same {@code format} with {@code force=true}.
 */
public record DevExportResultPacket(String generatedText, String resolvedPath,
                                     RequestDevExportPacket.Format format, Flags flags,
                                     String errorMessage) implements CustomPacketPayload {

    /** {@code isDevSource}/{@code writeSucceeded}/{@code requiresConfirmation} bundled into one sub-codec - {@code StreamCodec.composite} tops out at 6 fields, and the top-level packet already needs 5 slots without these three squeezed into one. */
    public record Flags(boolean isDevSource, boolean writeSucceeded, boolean requiresConfirmation) {}

    public DevExportResultPacket(String generatedText, String resolvedPath, RequestDevExportPacket.Format format,
                                  boolean isDevSource, boolean writeSucceeded, boolean requiresConfirmation, String errorMessage) {
        this(generatedText, resolvedPath, format, new Flags(isDevSource, writeSucceeded, requiresConfirmation), errorMessage);
    }

    public boolean isDevSource() { return flags.isDevSource(); }
    public boolean writeSucceeded() { return flags.writeSucceeded(); }
    public boolean requiresConfirmation() { return flags.requiresConfirmation(); }

    public static final Type<DevExportResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("multilib", "dev_export_result"));

    private static final StreamCodec<ByteBuf, Flags> FLAGS_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, Flags::isDevSource,
        ByteBufCodecs.BOOL, Flags::writeSucceeded,
        ByteBufCodecs.BOOL, Flags::requiresConfirmation,
        Flags::new
    );

    public static final StreamCodec<ByteBuf, DevExportResultPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DevExportResultPacket::generatedText,
        ByteBufCodecs.STRING_UTF8, DevExportResultPacket::resolvedPath,
        RequestDevExportPacket.FORMAT_CODEC, DevExportResultPacket::format,
        FLAGS_CODEC, DevExportResultPacket::flags,
        ByteBufCodecs.STRING_UTF8, DevExportResultPacket::errorMessage,
        DevExportResultPacket::new
    );

    @Override
    public Type<DevExportResultPacket> type() {
        return TYPE;
    }
}

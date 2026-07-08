package net.astronomy.multilib.client.network;

import net.astronomy.multilib.client.devtool.ClientMultiblockDevListHudState;
import net.astronomy.multilib.client.overlay.AutoPlacePreviewState;
import net.astronomy.multilib.client.overlay.GhostOverlayState;
import net.astronomy.multilib.core.devtool.MultiblockDevMenu;
import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.DevExportResultPacket;
import net.astronomy.multilib.network.DevListVisibilityPacket;
import net.astronomy.multilib.network.DevLoadListPacket;
import net.astronomy.multilib.network.DevLoadResultPacket;
import net.astronomy.multilib.network.DevScanResultPacket;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOverlayData(OverlayDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GhostOverlayState.INSTANCE.update(packet));
    }

    public static void handleAutoPlacePreviewData(AutoPlacePreviewDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AutoPlacePreviewState.INSTANCE.update(packet));
    }

    /**
     * Updates the currently open {@link MultiblockDevMenu} (if any) with the scan outcome. Silently
     * no-ops if the player's menu isn't a {@code MultiblockDevMenu} (e.g. the GUI was closed before the
     * server responded) - {@code MultiblockDevMenu} itself is the sole holder of this state, per the
     * fixed menu contract for this feature.
     */
    public static void handleDevScanResult(DevScanResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof MultiblockDevMenu menu
                    && menu.getDevBlockPos().equals(packet.devBlockPos())) {
                menu.applyScanResult(packet);
            }
            // Also feeds the scoreboard-style HUD list (see MultiblockDevListHudRenderer) - a no-op
            // unless this scan's position is the one currently active there, since the same packet
            // carries results for Detect, wrench tags, and the auto-detect tick alike.
            ClientMultiblockDevListHudState.updateIfActive(packet.devBlockPos(), packet.success() ? packet.scan() : null);
        });
    }

    /** Shows or hides the scoreboard-style HUD list for a dev-block - see {@link ClientMultiblockDevListHudState}. */
    public static void handleDevListVisibility(DevListVisibilityPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientMultiblockDevListHudState.setActive(packet.visible() ? packet.devBlockPos() : null));
    }

    /**
     * Updates the currently open {@link MultiblockDevMenu} (if any) with the export outcome, and always
     * copies the generated text to the clipboard here (not in the Screen) so the copy still happens even
     * if the GUI was closed in the meantime - see roadmap Design 5.
     * <p>
     * Deliberately does NOT force a client resource-pack reload on a successful export - an earlier
     * version of this did (to make the lang entry show up immediately), but a full
     * {@code Minecraft#reloadResourcePacks()} rebuilds every resource (textures, models, sounds, lang
     * alike) on every single export, which is a much bigger, more disruptive operation than the one-line
     * text change it was there to reveal - and no other part of this dev tool's workflow ever forced a
     * resource reload before. The lang entry still gets written correctly (see
     * {@code MultiblockDevOutputPaths#mergeLangEntry}); seeing it in-game just goes back to the same
     * manual F3+T (or reopening the world) every other resourcepack developer already uses.
     */
    public static void handleDevExportResult(DevExportResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(packet.generatedText());
            if (mc.player != null && mc.player.containerMenu instanceof MultiblockDevMenu menu) {
                menu.applyExportResult(packet);
            }
        });
    }

    /** Updates the GUI's Load tab list - see {@link net.astronomy.multilib.network.RequestDevLoadListPacket}. */
    public static void handleDevLoadList(DevLoadListPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof MultiblockDevMenu menu
                    && menu.getDevBlockPos().equals(packet.devBlockPos())) {
                menu.applyLoadList(packet);
            }
        });
    }

    /** Applies a completed Load (namespace/path/display-name/scan) - the Screen picks this up via {@link MultiblockDevMenu#getLoadVersion()}. */
    public static void handleDevLoadResult(DevLoadResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof MultiblockDevMenu menu
                    && menu.getDevBlockPos().equals(packet.devBlockPos())) {
                menu.applyLoadResult(packet);
            }
        });
    }

    /**
     * Standalone chat feedback for {@link net.astronomy.multilib.network.PreferredDefinitionResultPacket}
     * - not tied to whatever screen (if any) is currently open, since the picker screen that sent the
     * request already closed itself immediately on selection (see {@code MultiblockPreferenceScreen}),
     * well before this response could ever arrive.
     */
    public static void handlePreferredDefinitionResult(
            net.astronomy.multilib.network.PreferredDefinitionResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            if (packet.success()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "multilib.preference.set", packet.definitionId().toString()), false);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "multilib.preference.invalid", packet.definitionId().toString()), false);
            }
        });
    }
}

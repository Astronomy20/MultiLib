package net.astronomy.multilib.client.devtool;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockScanResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws the "Show/Hide List" HUD: a vanilla-scoreboard-sidebar-style overlay (right-aligned text lines,
 * each with its own translucent background strip) instead of a real GUI screen - the whole point of this
 * feature is to see a dev-block's scanned blocks/core/activation without having to open its GUI at all.
 * Content comes entirely from {@link ClientMultiblockDevListHudState}; this class only renders it.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class MultiblockDevListHudRenderer {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        BlockPos activePos = ClientMultiblockDevListHudState.getActivePos();
        if (activePos == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics guiGraphics = event.getGuiGraphics();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Multiblock Dev").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));

        MultiblockScanResult scan = ClientMultiblockDevListHudState.getScan();
        if (scan == null || scan.symbolToBlock().isEmpty()) {
            // Shown instead of just disappearing - the whole point of the HUD is to always confirm the
            // list is "on" and tell you what to do next, same as the GUI's own no-scan message, worded
            // for auto-detect (a one-shot Detect click no longer exists) rather than "click Detect".
            boolean autoDetectOn = mc.level != null
                    && mc.level.getBlockEntity(activePos) instanceof net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity be
                    && be.isAutoDetectOn();
            // Same color split as the GUI's own message: red when Detect is actually running and still
            // finding nothing (worth calling out), plain gray instruction when Detect is simply off.
            lines.add(autoDetectOn
                    ? Component.literal(net.astronomy.multilib.core.devtool.MultiblockScanner.EMPTY_AREA_MESSAGE).withStyle(ChatFormatting.RED)
                    : Component.literal("Turn on Detect").withStyle(ChatFormatting.GRAY));
        } else {
            if (scan.coreSymbol() != null) {
                lines.add(Component.literal("Core: " + blockName(scan, scan.coreSymbol())).withStyle(ChatFormatting.GREEN));
            } else if (scan.activationSymbol() != null) {
                lines.add(Component.literal("Activation: " + blockName(scan, scan.activationSymbol())).withStyle(ChatFormatting.AQUA));
            } else {
                lines.add(Component.literal("No core/activation tag yet").withStyle(ChatFormatting.GRAY));
            }

            for (Map.Entry<Character, net.minecraft.resources.ResourceLocation> entry : scan.symbolToBlock().entrySet()) {
                int count = scan.countOccurrences(entry.getKey());
                lines.add(Component.literal(entry.getKey() + " -> " + entry.getValue() + " (" + count + ")").withStyle(ChatFormatting.WHITE));
            }
        }

        int screenWidth = guiGraphics.guiWidth();
        int y = MARGIN;
        for (Component line : lines) {
            int width = font.width(line);
            int x = screenWidth - MARGIN - width;
            guiGraphics.fill(x - 3, y - 1, screenWidth - MARGIN + 3, y + LINE_HEIGHT - 2, 0x60000000);
            guiGraphics.drawString(font, line, x, y, 0xFFFFFF, true);
            y += LINE_HEIGHT;
        }
    }

    /** Block registry name, resolved from the scan's own symbol->block map (no world lookup needed). */
    private static String blockName(MultiblockScanResult scan, Character symbol) {
        net.minecraft.resources.ResourceLocation blockId = scan.symbolToBlock().get(symbol);
        return blockId != null ? blockId.toString() : String.valueOf(symbol);
    }
}

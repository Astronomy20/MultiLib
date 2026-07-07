package net.astronomy.multilib.compat.emi;

import net.astronomy.multilib.MultiLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

public class EmiInputBridge {

    private static boolean registered = false;

    public static void init() {
        if (!registered) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(EmiInputBridge.class);
            registered = true;
        }
    }

    private static class WidgetInfo {
        final MultiblockPreviewWidget widget;
        final double offsetX;
        final double offsetY;
        final int width;
        final int height;
        final long time;

        WidgetInfo(MultiblockPreviewWidget widget, double offsetX, double offsetY, int width, int height) {
            this.widget = widget;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.width = width;
            this.height = height;
            this.time = System.currentTimeMillis();
        }
    }

    private static final List<WidgetInfo> visibleWidgets = new ArrayList<>();

    public static void registerVisible(MultiblockPreviewWidget widget, double offsetX, double offsetY, int width, int height) {
        long now = System.currentTimeMillis();
        visibleWidgets.removeIf(info -> now - info.time > 100);
        visibleWidgets.removeIf(info -> info.widget == widget);
        visibleWidgets.add(new WidgetInfo(widget, offsetX, offsetY, width, height));
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        long now = System.currentTimeMillis();
        visibleWidgets.removeIf(info -> now - info.time > 100);

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        double scrollDelta = event.getScrollDeltaY();

        for (WidgetInfo info : visibleWidgets) {
            double localX = mouseX - info.offsetX;
            double localY = mouseY - info.offsetY;
            if (localX >= 0 && localX < info.width && localY >= 0 && localY < info.height) {
                if (info.widget.onScroll(localX, localY, scrollDelta)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /**
     * EMI's {@code dev.emi.emi.api.widget.Widget} exposes only {@code render}/{@code getTooltip}/
     * {@code mouseClicked}/{@code keyPressed} (verified via javap against
     * emi-neoforge-1.1.24+1.21.1-api.jar - no {@code mouseReleased} member exists on that class).
     * So, exactly like the scroll bridge above, actual mouse release is instead observed via
     * NeoForge's own screen event bus: {@code ScreenEvent.MouseButtonReleased.Pre} exists on every
     * NeoForge-hosted screen (confirmed via javap against
     * neoforge-21.1.213-universal.jar's {@code ScreenEvent$MouseButtonReleased} /
     * {@code ScreenEvent$MouseButtonReleased$Pre}, which exposes {@code getMouseX()}/
     * {@code getMouseY()}/{@code getButton()}) and needs no mixin. Every widget that registered
     * itself as visible this frame (via {@link #registerVisible}) gets notified so it can resolve its
     * own pending model click on the real release rather than a fixed timer.
     */
    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != 0) return;
        long now = System.currentTimeMillis();
        visibleWidgets.removeIf(info -> now - info.time > 100);

        // Deliberately not bounds-checked against (offsetX/Y, width, height): the press may have
        // started over the model and the release can legitimately land outside it after a drag, and
        // the widget's own pendingClick flag (already false in that case, cleared by onDrag) is what
        // actually decides whether anything happens - so it's safe, and correct, to just notify every
        // recently-visible widget unconditionally.
        for (WidgetInfo info : visibleWidgets) {
            info.widget.onMouseReleased();
        }
    }
}

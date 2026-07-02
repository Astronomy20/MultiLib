package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.compat.MultiblockPreviewPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * EMI equivalent of {@code MultiblockRecipeCategory} (JEI) / {@code MultiblockCategory} (REI): a
 * thin adapter that forwards EMI's input hooks into the viewer-agnostic {@link MultiblockPreviewPanel},
 * which owns all layout/rendering/view-state logic identically across all three integrations.
 * <p>
 * EMI's {@link Widget} only exposes {@link #render}, {@link #getTooltip}, {@link #mouseClicked}
 * and {@link #keyPressed} — there is no {@code mouseScrolled}/{@code mouseDragged} hook reaching
 * individual recipe widgets (verified against EMI 1.1.24 sources: {@code Widget.java} exposes
 * exactly those four overridable members, nothing else). Two workarounds are used, both avoiding
 * any mixin:
 * <ul>
 *   <li><b>Drag-to-rotate</b>: {@link #render} polls the left mouse button's physical state via
 *       {@code GLFW.glfwGetMouseButton} and compares this call's local mouse position against the
 *       last one seen while the button is held. Because EMI (like most Minecraft GUI code) can
 *       invoke {@code render()} more than once within what is visually a single displayed frame
 *       (e.g. tooltip/preview passes), naively treating every such call as a fresh drag sample would
 *       apply the rotation multiple times per real frame — this is exactly what produced the
 *       "exploded"/ghosted multi-copy rendering seen in testing. To prevent that, a drag sample is
 *       only accepted if at least {@link #MIN_SAMPLE_INTERVAL_NANOS} of real wall-clock time has
 *       passed since the last one, which comfortably separates genuine successive frames (even at
 *       240Hz, ~4.2ms apart) from same-frame duplicate invocations (sub-millisecond apart).</li>
 *   <li><b>Scroll-to-zoom / scroll-the-list</b>: mouse wheel motion is a discrete delta, not a
 *       polled state, so it can't be derived the same way. {@link EmiInputBridge} subscribes to
 *       NeoForge's {@code ScreenEvent.MouseScrolled.Pre} (fired for every {@link net.minecraft.client.gui.screens.Screen}
 *       including EMI's recipe screen, no mixin needed) and forwards the absolute screen mouse
 *       position; this widget resolves whether that position falls over its own area by comparing
 *       it against the offset it registers every {@link #render} call via
 *       {@link EmiInputBridge#registerVisible}.</li>
 * </ul>
 * State (zoom/yaw/pitch/layer/scroll/selection) lives directly on this widget instance: EMI
 * recreates recipe widgets whenever the recipe screen/page is rebuilt, so it naturally resets at
 * roughly the same granularity JEI/REI reset on screen-close — no dedicated handler is needed.
 */
public class MultiblockPreviewWidget extends Widget {

    /** Minimum real time between accepted drag samples — rejects same-frame duplicate render() calls. */
    private static final long MIN_SAMPLE_INTERVAL_NANOS = 3_000_000L; // 3 ms

    private final int x, y, width, height;
    private final Bounds bounds;
    private final MultiblockDefinition def;
    private final MultiblockPreviewPanel.ViewState vs;
    private final MultiblockPreviewPanel.Labels labels = new MultiblockPreviewPanel.Labels(
            Component.translatable("multilib.preview.layer_all"),
            Component.translatable("multilib.preview.required_blocks"));

    private int lastLocalMouseX = -1, lastLocalMouseY = -1;
    private boolean wasMouseDown = false;
    private long lastSampleNanos = 0L;

    /**
     * @param vs shared per-definition state (owned by {@code MultiblockEmiRecipe}'s static map, the
     *           same pattern REI/JEI use) rather than a fresh instance per widget — EMI recreates this
     *           widget every time its recipe screen/page is rebuilt, so a widget-local field would
     *           reset rotation/zoom/layer far more often than the user closing and reopening the
     *           recipe view, which was part of why rotation appeared to never "stick" in EMI.
     */
    public MultiblockPreviewWidget(MultiblockDefinition def, MultiblockPreviewPanel.ViewState vs, int x, int y, int width, int height) {
        this.def = def;
        this.vs = vs;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.bounds = new Bounds(x, y, width, height);
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    private MultiblockPreviewPanel.Layout layout() {
        return MultiblockPreviewPanel.layout(def, width, height);
    }

    // ── Rendering + polled drag ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // mouseX/mouseY here are already relative to this widget's own (x, y) origin per EMI's
        // WidgetGroup#render translation of the pose stack before calling widget.render(...).
        pollDrag(mouseX, mouseY);
        registerForScrollBridge(mouseX, mouseY);

        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        MultiblockPreviewPanel.render(gfx, def, vs, layout(), labels, mouseX, mouseY);
        gfx.pose().popPose();
    }

    /**
     * Polls the left mouse button and, only if enough real time has passed since the last accepted
     * sample (see {@link #MIN_SAMPLE_INTERVAL_NANOS}), applies a drag delta from the change in local
     * mouse position since that sample. Rejecting overly-frequent samples is what keeps duplicate
     * same-frame {@code render()} calls from each nudging the rotation, which is what caused the
     * ghosted/multi-copy rendering.
     */
    private void pollDrag(int mouseX, int mouseY) {
        boolean overWidget = mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        boolean mouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        long now = System.nanoTime();
        boolean enoughTimePassed = now - lastSampleNanos >= MIN_SAMPLE_INTERVAL_NANOS;

        if (enoughTimePassed) {
            if (mouseDown && wasMouseDown && overWidget && lastLocalMouseX >= 0
                    && (mouseX != lastLocalMouseX || mouseY != lastLocalMouseY)) {
                MultiblockPreviewPanel.onDrag(vs, layout(), mouseX, mouseY, mouseX - lastLocalMouseX, mouseY - lastLocalMouseY);
            }
            wasMouseDown = mouseDown;
            lastLocalMouseX = mouseX;
            lastLocalMouseY = mouseY;
            lastSampleNanos = now;
        }
    }

    /**
     * Registers this widget's current absolute-screen offset so {@link EmiInputBridge} can route
     * scroll events (which only carry absolute screen coordinates) to it. Uses the real GLFW cursor
     * position rather than the render-local mouseX/mouseY to compute the offset, since EMI's
     * absolute widget-group placement isn't otherwise exposed by the API.
     */
    private void registerForScrollBridge(int mouseX, int mouseY) {
        boolean overWidget = mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
        if (!overWidget) return;
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        double[] curX = new double[1], curY = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, curX, curY);
        var window = Minecraft.getInstance().getWindow();
        double guiScaleX = (double) window.getGuiScaledWidth() / window.getScreenWidth();
        double guiScaleY = (double) window.getGuiScaledHeight() / window.getScreenHeight();
        double absMouseX = curX[0] * guiScaleX;
        double absMouseY = curY[0] * guiScaleY;
        EmiInputBridge.registerVisible(this, absMouseX - mouseX, absMouseY - mouseY, width, height);
    }

    // ── Input: click (native Widget hook) ────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        MultiblockPreviewPanel.Layout lo = layout();
        boolean consumed = MultiblockPreviewPanel.onClick(vs, lo, def, mouseX, mouseY);
        return MultiblockPreviewPanel.armPendingClick(vs, lo, mouseX, mouseY) || consumed;
    }

    // ── Input: scroll (fed externally by EmiInputBridge with widget-local coordinates) ─────────

    public boolean onScroll(double localX, double localY, double scrollDeltaY) {
        return MultiblockPreviewPanel.onScroll(vs, layout(), def, localX, localY, scrollDeltaY);
    }

    // ── Input: mouse release (fed externally by EmiInputBridge, same mechanism as onScroll above) ──

    /**
     * Resolves a pending model-click (see {@link MultiblockPreviewPanel#onClick}) on the real mouse
     * release, fed in by {@link EmiInputBridge} via NeoForge's {@code ScreenEvent.MouseButtonReleased.Pre}
     * — EMI's own {@code Widget} has no {@code mouseReleased} hook to override directly. No
     * coordinates are needed: the pending click already recorded its own press-time position, and
     * only {@link MultiblockPreviewPanel.ViewState#pendingClick}'s current value decides whether this
     * does anything, so it's safe to call unconditionally on every release regardless of where the
     * cursor ended up.
     */
    public void onMouseReleased() {
        MultiblockPreviewPanel.resolveClickOnRelease(vs, layout(), def);
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────────────────────

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        return MultiblockPreviewPanel.tooltipAt(def, vs, layout(), mouseX, mouseY)
                .map(target -> {
                    Component text = switch (target) {
                        case MultiblockPreviewPanel.TooltipTarget.Title t -> Component.literal(t.fullName());
                        case MultiblockPreviewPanel.TooltipTarget.RotateBadge b -> Component.translatable(b.autoRotateOn()
                                ? "multilib.preview.auto_rotate.on" : "multilib.preview.auto_rotate.off");
                        case MultiblockPreviewPanel.TooltipTarget.ListRow row -> Component.literal(
                                "x" + row.count() + " " + row.stack().getHoverName().getString());
                    };
                    return ClientTooltipComponent.create(text.getVisualOrderText());
                })
                .map(List::of)
                .orElseGet(List::of);
    }
}

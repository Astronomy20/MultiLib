package net.astronomy.multilib.compat;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.client.ClientConfig;
import net.astronomy.multilib.client.render.MultiblockStructurePreviewRenderer;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewer-agnostic 3D multiblock preview: layout, per-definition view state, rendering, and input
 * handling shared identically by the JEI/REI/EMI recipe-browser integrations, so all three present
 * the exact same GUI instead of three independently-drifting reimplementations.
 * <p>
 * Each viewer's compat package owns only the thin adapter needed to plug into that library's widget
 * API (how input events reach {@link #onScroll}/{@link #onDrag}/{@link #onClick}, and how tooltips
 * get surfaced) — everything about layout, rendering, and view state lives here exactly once.
 * <p>
 * All coordinates taken by / returned from this class are <b>panel-local</b>: (0,0) is the preview's
 * own top-left corner. Callers are responsible for translating their own GuiGraphics pose stack (and
 * their own mouse coordinates) into that local space before calling in.
 */
public final class MultiblockPreviewPanel {

    private MultiblockPreviewPanel() {}

    // ── Layout constants (identical proportions across all three viewers) ──────────────────────
    private static final int TITLE_H = 12;
    private static final float MODEL_AREA_RATIO = 2f / 3f;
    private static final int LR_H = 14;
    private static final int ARR_W = 20;
    private static final int ROW_H = 18;
    private static final int SB_W = 6;
    private static final int P_X1 = 4;
    private static final int BADGE_SZ = 15;
    private static final int BADGE_PAD = 2;
    private static final int BADGE_ICON_SZ = 11;
    private static final int MODEL_AREA_MARGIN = 2;

    // ── Rotation / animation constants ──────────────────────────────────────────────────────────
    private static final float DEFAULT_PITCH    = 22f;
    private static final float DRAG_SENSITIVITY = 0.5f;
    private static final float ZOOM_STEP        = 0.1f;
    private static final float ZOOM_MIN         = 0.3f;
    private static final float ZOOM_MAX         = 3.0f;
    private static final float AUTO_YAW_MS      = 360f / 8000f; // full cycle in 8 s
    private static final long  DRAG_PAUSE_MS    = 500L;

    private static final ResourceLocation ROTATE_ICON_ON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "textures/gui/rotate_icon.png");
    private static final ResourceLocation ROTATE_ICON_OFF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "textures/gui/rotate_icon_off.png");

    // ── Per-definition view state — one instance lives as long as each viewer's own lifecycle
    //    dictates (JEI/REI: keyed map cleared when their recipes screen closes; EMI: one instance
    //    per recipe widget, which EMI itself recreates on screen/page rebuild). ─────────────────
    public static final class ViewState {
        public float yaw          = 0f;
        public float pitch        = DEFAULT_PITCH;
        public float zoom         = 1.0f;
        public Integer layer      = null; // null = show all layers
        public int scroll         = 0;    // first visible index in the required-blocks list
        public long lastDragTime  = 0L;
        public MultiblockStructurePreviewRenderer.BlockHit selectedHit = null;
        public final Ticker titleTicker = new Ticker();
        public final Ticker labelTicker = new Ticker();

        // A press on the model doesn't pick a block immediately — it's only resolved into an actual
        // block-pick once the mouse is actually released (see resolveClickOnRelease), and only if no
        // rotate-drag cancelled it first (see onDrag's pendingClick=false). This is release-driven
        // rather than a fixed timer so a slow drag that hasn't been detected yet can't have its click
        // confirmed out from under it before the rotation starts.
        boolean pendingClick = false;
        double pendingClickX, pendingClickY;

        // True from the first onDrag call of a gesture until release (see resolveClickOnRelease,
        // which clears it). Distinct from lastDragTime/DRAG_PAUSE_MS below: that timer governs when
        // auto-rotate is allowed to resume, which is a genuinely time-based question either way. This
        // flag instead answers "is a drag gesture currently in progress", so the yaw catch-up-snap in
        // onDrag only ever fires once per gesture (right when manual control actually starts), not
        // every time the user briefly stops moving the mouse for >DRAG_PAUSE_MS while still holding
        // the button — which is what caused the model to visibly "jump" mid-rotation.
        boolean dragActive = false;

        // Snapshot of the exact (yaw, pitch, layer, centerX, centerY, viewSize) that the LAST render()
        // call actually drew on screen. Picking must resolve against this snapshot rather than
        // recomputing effectiveYaw()/etc. fresh at press/release time: those are separate input-event
        // callbacks that fire at their own instants, potentially tens of milliseconds after the frame
        // the user actually saw and clicked on. With auto-rotate on (the default), yaw is a function of
        // System.currentTimeMillis(), so recomputing it at click/release time silently picks against a
        // slightly-rotated-further model than what was rendered — exactly the "ignores the block I
        // actually clicked" symptom reported, most reproducible while the model is spinning. Freezing
        // the pick to what render() last drew removes that drift entirely.
        boolean hasLastRender = false;
        float lastRenderYaw, lastRenderPitch;
        int lastRenderCenterX, lastRenderCenterY, lastRenderViewSize;
        Integer lastRenderOnlyLayerIndex;
    }

    public static final class Ticker {
        long scrollMs = 0L;
        long lastFrameNanos = 0L;
    }

    public static boolean effectiveAutoRotate(ViewState vs) {
        boolean dragPaused = System.currentTimeMillis() - vs.lastDragTime < DRAG_PAUSE_MS;
        return ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get() && !dragPaused;
    }

    public static float effectiveYaw(ViewState vs) {
        return effectiveAutoRotate(vs)
                ? (System.currentTimeMillis() % 8000L) * AUTO_YAW_MS
                : vs.yaw;
    }

    // ── Layout — derived once per render/input call from (width, height, itemCount) ─────────────

    public static final class Layout {
        public final int width, height, itemCount, nLayers;

        Layout(int width, int height, int itemCount, int nLayers) {
            this.width = width;
            this.height = height;
            this.itemCount = itemCount;
            this.nLayers = nLayers;
        }

        private int contentTop()    { return TITLE_H + 2; }
        private int contentBottom() { return height - 2; }
        private int contentH()      { return contentBottom() - contentTop(); }

        private int listSectionH() {
            int maxListH = Math.round(contentH() * (1f - MODEL_AREA_RATIO));
            int neededH = 15 + itemCount * ROW_H + 2;
            return Math.min(neededH, maxListH);
        }
        public int listSectionTop() { return contentBottom() - listSectionH(); }
        private int modelSectionH() { return listSectionTop() - contentTop(); }

        private int layerRowH() { return nLayers > 1 ? LR_H : 0; }
        public int lrY() { return contentTop() + modelSectionH() - layerRowH(); }

        public int pX1() { return P_X1; }
        public int pX2() { return width - P_X1; }
        public int arrL() { return P_X1; }
        public int arrR() { return pX2() - ARR_W; }
        public int pY1() { return contentTop() + 1; }
        public int pY2() { return lrY() - 2; }
        public int pCy() { return (pY1() + pY2()) / 2; }

        public int modelLeft()   { return P_X1 + MODEL_AREA_MARGIN; }
        public int modelRight()  { return pX2() - MODEL_AREA_MARGIN; }
        public int modelTop()    { return pY1() + MODEL_AREA_MARGIN; }
        public int modelBottom() { return lrY() - MODEL_AREA_MARGIN; }
        public int pCx()         { return (modelLeft() + modelRight()) / 2; }
        public int patternSize() { return Math.min(modelRight() - modelLeft(), pY2() - pY1()); }

        public int autoX() { return modelRight() - BADGE_SZ - BADGE_PAD; }
        public int autoY() { return modelTop() + BADGE_PAD; }

        public int selX() { return modelLeft() + BADGE_PAD; }
        public int selY() { return modelTop() + BADGE_PAD; }

        public int lblY()  { return listSectionTop() + 3; }
        public int listY() { return lblY() + 12; }
        public int listH() { return listSectionTop() + listSectionH() - listY() - 2; }
        public int maxVis() { return Math.max(1, listH() / ROW_H); }
    }

    public static Layout layout(MultiblockDefinition def, int width, int height) {
        return new Layout(width, height, countItems(def).size(), def.getLayerCount());
    }

    // ── Text labels shown in the panel — callers pass already-resolved Components so each viewer
    //    can keep using its own lang-key convention where required (category title stays per-viewer;
    //    these three are free-standing strings we control, unified under shared "multilib.preview.*"
    //    keys — see assets/multilib/lang/en_us.json). ───────────────────────────────────────────
    public record Labels(Component layerAll, Component requiredBlocks) {}

    // ── Rendering ────────────────────────────────────────────────────────────────────────────────

    /**
     * Draws the whole panel (title, 3D model, layer nav, required-blocks list, badges) into
     * {@code gfx}, whose pose stack must already be translated so (0,0) is this panel's top-left
     * corner. {@code mouseX}/{@code mouseY} must already be in that same local space.
     */
    public static void render(GuiGraphics gfx, MultiblockDefinition def, ViewState vs, Layout lo,
                               Labels labels, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;

        // ── Title row ──
        String title = multiblockName(def);
        int maxTitleW = lo.width - 2 * P_X1;
        boolean hoveringTitle = mouseX >= 0 && mouseX < lo.width && mouseY >= 0 && mouseY < TITLE_H;
        if (font.width(title) <= maxTitleW) {
            drawCenteredNoShadow(gfx, font, title, lo.width / 2, (TITLE_H - font.lineHeight) / 2, 0x404040);
        } else {
            drawTicker(gfx, font, title, P_X1, (TITLE_H - font.lineHeight) / 2, maxTitleW, 0x404040, hoveringTitle, vs.titleTicker);
        }

        // ── 3D model ──
        Integer onlyLayerIndex = vs.layer == null ? null : (lo.nLayers - 1 - vs.layer);
        float yaw = effectiveYaw(vs);
        int centerX = lo.pCx(), centerY = lo.pCy();
        int viewSize = Math.round(lo.patternSize() * vs.zoom);
        gfx.flush();
        int[] scissorMin = toAbsoluteScreen(gfx, lo.modelLeft(), lo.modelTop());
        int[] scissorMax = toAbsoluteScreen(gfx, lo.modelRight(), lo.modelBottom());
        gfx.enableScissor(scissorMin[0], scissorMin[1], scissorMax[0], scissorMax[1]);
        MultiblockStructurePreviewRenderer.render(
                gfx, def, centerX, centerY, viewSize, yaw, vs.pitch, onlyLayerIndex, vs.selectedHit);
        gfx.disableScissor();

        // Snapshot exactly what was just drawn so a later click/release picks against the frame the
        // user actually saw (see ViewState.lastRenderYaw's javadoc for why this must not be
        // recomputed fresh at click time).
        vs.hasLastRender = true;
        vs.lastRenderYaw = yaw;
        vs.lastRenderPitch = vs.pitch;
        vs.lastRenderCenterX = centerX;
        vs.lastRenderCenterY = centerY;
        vs.lastRenderViewSize = viewSize;
        vs.lastRenderOnlyLayerIndex = onlyLayerIndex;

        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 300);

        // ── Layer navigation ──
        if (lo.nLayers > 1) {
            String layerLabel = vs.layer == null ? labels.layerAll().getString() : (vs.layer + 1) + " / " + lo.nLayers;
            int labelColor = vs.layer == null ? 0x505050 : 0x2060C8;
            int lrY = lo.lrY();
            boolean hoverL = mouseX >= lo.arrL() && mouseX < lo.arrL() + ARR_W && mouseY >= lrY && mouseY < lrY + LR_H;
            boolean hoverR = mouseX >= lo.arrR() && mouseX < lo.arrR() + ARR_W && mouseY >= lrY && mouseY < lrY + LR_H;

            gfx.fill(lo.arrL(), lrY, lo.arrL() + ARR_W, lrY + LR_H, hoverL ? 0xFF909090 : 0xFF707070);
            drawCenteredNoShadow(gfx, font, "◀", lo.arrL() + ARR_W / 2, lrY + (LR_H - font.lineHeight) / 2, 0xFFFFFF);
            gfx.fill(lo.arrR(), lrY, lo.arrR() + ARR_W, lrY + LR_H, hoverR ? 0xFF909090 : 0xFF707070);
            drawCenteredNoShadow(gfx, font, "▶", lo.arrR() + ARR_W / 2, lrY + (LR_H - font.lineHeight) / 2, 0xFFFFFF);
            drawCenteredNoShadow(gfx, font, layerLabel, lo.width / 2, lrY + (LR_H - font.lineHeight) / 2, labelColor);
        }

        // ── "Required blocks:" label ──
        gfx.drawString(font, labels.requiredBlocks().getString(), P_X1, lo.lblY(), 0x404040, false);

        gfx.pose().popPose();

        // ── Badges (rotate-state, selected-block) drawn above the model ──
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 400);
        boolean persistentAutoRotate = ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get();
        boolean hoverAuto = mouseX >= lo.autoX() && mouseX < lo.autoX() + BADGE_SZ && mouseY >= lo.autoY() && mouseY < lo.autoY() + BADGE_SZ;
        gfx.fill(lo.autoX(), lo.autoY(), lo.autoX() + BADGE_SZ, lo.autoY() + BADGE_SZ, hoverAuto ? 0xD0000000 : 0xA0000000);
        drawRotateIcon(gfx, lo.autoX() + (BADGE_SZ - BADGE_ICON_SZ) / 2, lo.autoY() + (BADGE_SZ - BADGE_ICON_SZ) / 2, BADGE_ICON_SZ, persistentAutoRotate);

        if (vs.selectedHit != null) {
            BlockIngredient selIng = def.getBlockMap().get(vs.selectedHit.symbol());
            if (selIng != null) {
                ItemStack selStack = representativeStack(selIng);
                if (!selStack.isEmpty()) {
                    String name = selStack.getHoverName().getString();
                    int maxTextW = 80;
                    int nameW = font.width(name);
                    int renderTextW = Math.min(nameW, maxTextW);
                    int boxW = 16 + 4 + renderTextW + 8;
                    int boxH = 20;
                    int boxX = lo.selX();
                    int boxY = lo.selY();
                    float scale = 0.75f;

                    gfx.pose().pushPose();
                    gfx.pose().translate(boxX, boxY, 0);
                    gfx.pose().scale(scale, scale, 1.0f);
                    gfx.fill(0, 0, boxW, boxH, 0xD0000000);
                    gfx.renderItem(selStack, 2, 2);
                    if (nameW <= maxTextW) {
                        gfx.drawString(font, name, 20, (boxH - font.lineHeight) / 2, 0xFFFFFF, false);
                    } else {
                        boolean hovering = mouseX >= boxX && mouseX < boxX + (boxW * scale) && mouseY >= boxY && mouseY < boxY + (boxH * scale);
                        drawTicker(gfx, font, name, 20, (boxH - font.lineHeight) / 2, maxTextW, 0xFFFFFF, hovering, vs.labelTicker);
                    }
                    gfx.pose().popPose();
                }
            }
        }
        gfx.pose().popPose();

        // ── Required-blocks list (scrollable) ──
        renderList(gfx, font, def, vs, lo, mouseX, mouseY);
    }

    private static void renderList(GuiGraphics gfx, Font font, MultiblockDefinition def, ViewState vs, Layout lo, int mouseX, int mouseY) {
        List<Map.Entry<ItemStack, Integer>> items = countItems(def);
        int total = items.size();
        int maxV = lo.maxVis();
        int maxScroll = Math.max(0, total - maxV);
        vs.scroll = Math.max(0, Math.min(maxScroll, vs.scroll));
        int visible = Math.min(maxV, total - vs.scroll);

        int lY = lo.listY();
        int listRight = lo.width - SB_W - 3;

        for (int i = 0; i < visible; i++) {
            Map.Entry<ItemStack, Integer> e = items.get(vs.scroll + i);
            int rowY = lY + i * ROW_H;
            int slotX = P_X1 + 1;
            int slotY = rowY + (ROW_H - 18) / 2;

            boolean hoverRow = mouseX >= slotX && mouseX < listRight && mouseY >= rowY && mouseY < rowY + ROW_H;
            gfx.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, hoverRow ? 0x40FFFFFF : 0x30000000);
            gfx.renderItem(e.getKey(), slotX, slotY);

            int count = e.getValue();
            if (count > 1) {
                String label = String.valueOf(count);
                gfx.pose().pushPose();
                gfx.pose().translate(slotX + 17, slotY + 16 - font.lineHeight, 200);
                gfx.drawString(font, label, -font.width(label), 0, 0xFFFFFF, false);
                gfx.pose().popPose();
            }

            String name = e.getKey().getHoverName().getString();
            int maxNameW = listRight - (P_X1 + 20) - 2;
            if (font.width(name) > maxNameW) {
                while (name.length() > 1 && font.width(name + "…") > maxNameW) name = name.substring(0, name.length() - 1);
                name += "…";
            }
            gfx.drawString(font, name, P_X1 + 20, rowY + (ROW_H - font.lineHeight) / 2, 0x303030, false);
        }

        if (maxScroll > 0) {
            int lH = lo.listH();
            int sbX = lo.width - SB_W - 2;
            int thumbH = Math.max(8, lH * maxV / total);
            int thumbY = lY + (lH - thumbH) * vs.scroll / maxScroll;
            gfx.fill(sbX, lY, sbX + SB_W, lY + lH, 0xFF8B8B8B);
            gfx.fill(sbX, thumbY, sbX + SB_W, thumbY + thumbH, 0xFFFFFFFF);
        }
    }

    // ── Input (panel-local coordinates; callers translate from their own event's coordinates) ──

    /** @return true if the scroll was consumed (over the model → zoom; over the list → scroll it). */
    public static boolean onScroll(ViewState vs, Layout lo, double localX, double localY, double deltaY) {
        if (localX >= lo.pX1() && localX <= lo.pX2() && localY >= lo.pY1() && localY <= lo.pY2()) {
            vs.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, vs.zoom + (deltaY > 0 ? ZOOM_STEP : -ZOOM_STEP)));
            return true;
        }
        int lY = lo.listY(), lH = lo.listH();
        if (localX >= lo.pX1() && localX <= lo.width - 2 && localY >= lY && localY <= lY + lH) {
            int maxScroll = Math.max(0, lo.itemCount - lo.maxVis());
            vs.scroll = Math.max(0, Math.min(maxScroll, vs.scroll + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        return false;
    }

    /** @return true if the drag was consumed (scrollbar drag, or model rotate). */
    public static boolean onDrag(ViewState vs, Layout lo, double localX, double localY, double dx, double dy) {
        int sbX = lo.width - SB_W - 2;
        int lY = lo.listY(), lH = lo.listH();
        if (localX >= sbX && localX <= lo.width - 2 && localY >= lY && localY <= lY + lH) {
            scrollToMouseY(vs, lo, localY, lY, lH);
            return true;
        }
        if (localX < 0 || localX >= lo.width || localY < 0 || localY >= lo.height) return false;
        // This gesture just turned into a rotate-drag — whatever was under the initial press no
        // longer counts as a click (see resolveClickOnRelease / armPendingClick).
        vs.pendingClick = false;
        if (!vs.dragActive) {
            // Only snap yaw to match wherever auto-rotate currently is at the START of a drag
            // gesture (first sample since the button went down) — not every time DRAG_PAUSE_MS
            // happens to have elapsed since the last *sample*, which also happens whenever the user
            // simply holds still for a moment mid-drag without releasing, and previously made the
            // model visibly jump on resuming movement even though nothing was actually released.
            if (System.currentTimeMillis() - vs.lastDragTime >= DRAG_PAUSE_MS) {
                vs.yaw = (System.currentTimeMillis() % 8000L) * AUTO_YAW_MS;
            }
            vs.dragActive = true;
        }
        vs.yaw += (float) dx * DRAG_SENSITIVITY;
        vs.pitch = Math.max(-80f, Math.min(80f, vs.pitch + (float) dy * DRAG_SENSITIVITY));
        vs.lastDragTime = System.currentTimeMillis();
        return true;
    }

    /**
     * Arms a pending model click if ({@code localX}, {@code localY}) lands on the model (and not on
     * the selected-block badge). Safe to call from a dry-run/probe pass — arming has no visible
     * effect on its own; it's only actually applied by {@link #resolveClickOnRelease}, and only if a
     * rotate-drag hasn't cancelled it first (see {@link #onDrag}). Callers invoke this once per
     * genuine press: REI/EMI's single {@code mouseClicked}, or JEI's {@code handleInput} on the
     * simulate/probe pass specifically (JEI's real release pass must NOT call this again — see
     * {@link #resolveClickOnRelease}'s javadoc for why re-arming on release would undo the very
     * cancellation this mechanism exists for).
     *
     * @return true if the model area was hit (and a click is now pending).
     */
    public static boolean armPendingClick(ViewState vs, Layout lo, double localX, double localY) {
        boolean overSelBadge = vs.selectedHit != null
                && localX >= lo.selX() && localX < lo.selX() + BADGE_SZ && localY >= lo.selY() && localY < lo.selY() + BADGE_SZ;
        if (!overSelBadge && localX >= lo.modelLeft() && localX < lo.modelRight() && localY >= lo.modelTop() && localY < lo.modelBottom()) {
            vs.pendingClick = true;
            vs.pendingClickX = localX;
            vs.pendingClickY = localY;
            return true;
        }
        return false;
    }

    /**
     * Applies a pending model click (armed by {@link #armPendingClick}) once the mouse button has
     * actually been released, provided no rotate-drag cancelled it first (see {@link #onDrag}, which
     * clears {@code pendingClick} the moment real drag motion is observed). Also ends the current drag
     * gesture ({@code dragActive}), regardless of whether a click was pending. Callers must invoke
     * this from their viewer's own mouse-release hook — {@code mouseReleased} for REI/EMI, or the
     * real (non-simulate) pass of JEI's {@code handleInput} — passing the same panel-local
     * coordinates used elsewhere. Using the real release event instead of a fixed timer means a click
     * can never be confirmed while the button is still down, however slowly the drag that will cancel
     * it develops.
     *
     * @return true if a pending click was present and consumed (whether or not it changed selection).
     */
    public static boolean resolveClickOnRelease(ViewState vs, Layout lo, MultiblockDefinition def) {
        vs.dragActive = false;
        if (!vs.pendingClick) return false;
        vs.pendingClick = false;
        // Pick against the exact (yaw, pitch, layer, center, viewSize) the last render() call actually
        // drew — NOT a freshly recomputed effectiveYaw()/lo, which (with auto-rotate on, the default)
        // would have silently advanced past what the user saw and clicked on by however many
        // milliseconds elapsed between that frame and this release event. Falls back to a fresh
        // computation only if render() genuinely hasn't run yet (defensive; shouldn't happen in
        // practice since a click can't be pending without a prior render).
        MultiblockStructurePreviewRenderer.BlockHit hit;
        if (vs.hasLastRender) {
            hit = MultiblockStructurePreviewRenderer.pick(
                    def, vs.lastRenderCenterX, vs.lastRenderCenterY, vs.lastRenderViewSize,
                    vs.lastRenderYaw, vs.lastRenderPitch, vs.lastRenderOnlyLayerIndex,
                    vs.pendingClickX, vs.pendingClickY);
        } else {
            Integer onlyLayerIndex = vs.layer == null ? null : (lo.nLayers - 1 - vs.layer);
            hit = MultiblockStructurePreviewRenderer.pick(
                    def, lo.pCx(), lo.pCy(), Math.round(lo.patternSize() * vs.zoom), effectiveYaw(vs), vs.pitch,
                    onlyLayerIndex, vs.pendingClickX, vs.pendingClickY);
        }
        vs.selectedHit = (hit != null && hit.equals(vs.selectedHit)) ? null : hit;
        return true;
    }

    private static void scrollToMouseY(ViewState vs, Layout lo, double localY, int lY, int lH) {
        int maxScroll = Math.max(0, lo.itemCount - lo.maxVis());
        if (maxScroll == 0) { vs.scroll = 0; return; }
        double t = (localY - lY) / (double) lH;
        vs.scroll = Math.max(0, Math.min(maxScroll, (int) Math.round(t * maxScroll)));
    }

    /** Equivalent to {@code onClick(vs, lo, def, localX, localY, false)}. */
    public static boolean onClick(ViewState vs, Layout lo, MultiblockDefinition def, double localX, double localY) {
        return onClick(vs, lo, def, localX, localY, false);
    }

    /**
     * @param simulate if true, only reports whether the click WOULD be consumed — no state is
     *                 mutated. Needed by viewers (JEI) whose input API probes handlers with a
     *                 simulate pass before committing the real one, to avoid double-applying
     *                 toggles/layer changes/selection when both passes reach here.
     * @return true if the click was (or would be) consumed.
     */
    public static boolean onClick(ViewState vs, Layout lo, MultiblockDefinition def, double localX, double localY, boolean simulate) {
        // Rotate-state badge: click to toggle the persistent auto-rotate preference.
        if (localX >= lo.autoX() && localX < lo.autoX() + BADGE_SZ && localY >= lo.autoY() && localY < lo.autoY() + BADGE_SZ) {
            if (!simulate) {
                boolean current = ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get();
                if (current) vs.yaw = effectiveYaw(vs); // freeze in place when turning auto-rotate off
                ClientConfig.JEI_PREVIEW_AUTO_ROTATE.set(!current);
            }
            return true;
        }

        // Scrollbar track click → jump-scroll.
        int sbX = lo.width - SB_W - 2;
        int lY = lo.listY(), lH = lo.listH();
        if (localX >= sbX && localX <= lo.width - 2 && localY >= lY && localY <= lY + lH) {
            if (!simulate) scrollToMouseY(vs, lo, localY, lY, lH);
            return true;
        }

        // Click on the model is handled by armPendingClick (called separately by each viewer at the
        // right press-time moment) + resolveClickOnRelease — not here. See armPendingClick's javadoc
        // for why this needs its own entry point instead of living in this simulate-gated method:
        // JEI's press is a simulate=true probe and its release is simulate=false, the OPPOSITE of
        // what this method's `simulate` flag would otherwise gate arming behind.

        // Layer arrows (only when multiple layers exist).
        int layerY = lo.lrY();
        if (lo.nLayers > 1 && localY >= layerY && localY < layerY + LR_H) {
            boolean hitLeft = localX >= lo.arrL() && localX < lo.arrL() + ARR_W;
            boolean hitRight = localX >= lo.arrR() && localX < lo.arrR() + ARR_W;
            if (hitLeft || hitRight) {
                if (!simulate) {
                    if (hitLeft) {
                        if (vs.layer == null) vs.layer = lo.nLayers - 1;
                        else if (vs.layer == 0) vs.layer = null;
                        else vs.layer = vs.layer - 1;
                    } else {
                        if (vs.layer == null) vs.layer = 0;
                        else if (vs.layer >= lo.nLayers - 1) vs.layer = null;
                        else vs.layer = vs.layer + 1;
                    }
                }
                return true;
            }
        }
        return false;
    }

    // ── Tooltip target lookup — callers translate the result into whatever tooltip mechanism
    //    their own viewer API uses (JEI: ITooltipBuilder; REI: Tooltip.queue() in render(); EMI:
    //    getTooltip()). Keeping the actual Component text/logic here means all three read the same
    //    words, not three independently-typed copies. ─────────────────────────────────────────────

    public sealed interface TooltipTarget {
        record Title(String fullName) implements TooltipTarget {}
        record RotateBadge(boolean autoRotateOn) implements TooltipTarget {}
        record ListRow(ItemStack stack, int count) implements TooltipTarget {}
    }

    public static java.util.Optional<TooltipTarget> tooltipAt(MultiblockDefinition def, ViewState vs, Layout lo, double localX, double localY) {
        if (localX >= 0 && localX < lo.width && localY >= 0 && localY < TITLE_H) {
            return java.util.Optional.of(new TooltipTarget.Title(multiblockName(def)));
        }
        if (localX >= lo.autoX() && localX < lo.autoX() + BADGE_SZ && localY >= lo.autoY() && localY < lo.autoY() + BADGE_SZ) {
            return java.util.Optional.of(new TooltipTarget.RotateBadge(ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get()));
        }
        int lY = lo.listY();
        int maxV = lo.maxVis();
        List<Map.Entry<ItemStack, Integer>> items = countItems(def);
        int visible = Math.min(maxV, items.size() - vs.scroll);
        for (int i = 0; i < visible; i++) {
            int rowY = lY + i * ROW_H;
            if (localX >= P_X1 + 1 && localX < lo.width - SB_W && localY >= rowY && localY < rowY + ROW_H) {
                Map.Entry<ItemStack, Integer> e = items.get(vs.scroll + i);
                return java.util.Optional.of(new TooltipTarget.ListRow(e.getKey(), e.getValue()));
            }
        }
        return java.util.Optional.empty();
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────────────────────────

    private static void drawRotateIcon(GuiGraphics gfx, int x, int y, int size, boolean enabled) {
        ResourceLocation tex = enabled ? ROTATE_ICON_ON_TEXTURE : ROTATE_ICON_OFF_TEXTURE;
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        float scale = size / 16f;
        gfx.pose().scale(scale, scale, 1f);
        gfx.blit(tex, 0, 0, 0, 0, 16, 16, 16, 16);
        gfx.pose().popPose();
    }

    private static void drawTicker(GuiGraphics gfx, Font font, String text, int x, int y, int maxWidth,
                                    int color, boolean hovering, Ticker ticker) {
        long nowNanos = System.nanoTime();
        if (ticker.lastFrameNanos != 0L && !hovering) {
            ticker.scrollMs += (nowNanos - ticker.lastFrameNanos) / 1_000_000L;
        }
        ticker.lastFrameNanos = nowNanos;

        int textW = font.width(text);
        int gap = 24;
        int cycle = textW + gap;
        int scrollPxPerSec = 25;
        int offset = (int) ((ticker.scrollMs / 1000.0) * scrollPxPerSec) % cycle;

        int[] sMin = toAbsoluteScreen(gfx, x, y - 1);
        int[] sMax = toAbsoluteScreen(gfx, x + maxWidth, y + font.lineHeight + 1);
        gfx.enableScissor(sMin[0], sMin[1], sMax[0], sMax[1]);
        int x0 = x - offset;
        gfx.drawString(font, text, x0, y, color, false);
        gfx.drawString(font, text, x0 + cycle, y, color, false);
        gfx.disableScissor();
    }

    private static void drawCenteredNoShadow(GuiGraphics gfx, Font font, String text, int centerX, int y, int color) {
        gfx.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static int[] toAbsoluteScreen(GuiGraphics gfx, int x, int y) {
        org.joml.Vector3f v = new org.joml.Vector3f(x, y, 0);
        v.mulPosition(gfx.pose().last().pose());
        return new int[]{Math.round(v.x), Math.round(v.y)};
    }

    // ── Shared pure helpers (single canonical copy — was triplicated across compat/jei, compat/rei,
    //    compat/emi) ──────────────────────────────────────────────────────────────────────────────

    /**
     * Counts occurrences of every block symbol across all layers. Sorted so the structure's core
     * block ranks first, IO-port blocks rank next, and everything else follows by count descending
     * (count descending is also the tiebreak within the core/io-port/normal groups).
     */
    public static List<Map.Entry<ItemStack, Integer>> countItems(MultiblockDefinition def) {
        Map<Character, Integer> symCount = new LinkedHashMap<>();
        for (List<String> layer : def.getLayers()) {
            for (String row : layer) {
                for (char c : row.toCharArray()) {
                    if (c != ' ') symCount.merge(c, 1, Integer::sum);
                }
            }
        }
        record Ranked(ItemStack stack, int count, int rank) {}
        List<Ranked> ranked = new ArrayList<>();
        Map<Character, BlockIngredient> blockMap = def.getBlockMap();
        for (Map.Entry<Character, Integer> e : symCount.entrySet()) {
            char symbol = e.getKey();
            BlockIngredient ing = blockMap.get(symbol);
            if (ing == null) continue;
            ItemStack stack = representativeStack(ing);
            if (stack.isEmpty()) continue;
            ranked.add(new Ranked(stack, e.getValue(), symbolRank(def, symbol, ing)));
        }
        ranked.sort((a, b) -> {
            if (a.rank() != b.rank()) return Integer.compare(a.rank(), b.rank());
            return Integer.compare(b.count(), a.count());
        });
        List<Map.Entry<ItemStack, Integer>> result = new ArrayList<>();
        for (Ranked r : ranked) result.add(Map.entry(r.stack(), r.count()));
        return result;
    }

    /** 0 = core symbol, 1 = IO-port symbol, 2 = everything else. */
    private static int symbolRank(MultiblockDefinition def, char symbol, BlockIngredient ing) {
        if (def.hasCore() && symbol == def.getCoreSymbol()) return 0;
        for (Block block : ing.getCandidateBlocks()) {
            if (BlockDefinitionRegistry.get(block).map(BlockDefinition::isIoPort).orElse(false)) {
                return 1;
            }
        }
        return 2;
    }

    public static ItemStack representativeStack(BlockIngredient ingredient) {
        for (Block block : ingredient.getCandidateBlocks()) {
            Item item = block.asItem();
            if (item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    /** The multiblock's "name", for the title row — the core/activation block's own display name. */
    public static String multiblockName(MultiblockDefinition def) {
        if (def.getNameTranslationKey().isPresent()) {
            return Component.translatable(def.getNameTranslationKey().get()).getString();
        }
        char actSym = def.getActivationSymbol();
        char corSym = def.getCoreSymbol();
        char disSym = actSym != '\0' ? actSym : corSym;
        if (disSym != '\0') {
            BlockIngredient ing = def.getBlockMap().get(disSym);
            if (ing != null) {
                ItemStack stack = representativeStack(ing);
                if (!stack.isEmpty()) return stack.getHoverName().getString();
            }
        }
        return def.getId().toString();
    }
}

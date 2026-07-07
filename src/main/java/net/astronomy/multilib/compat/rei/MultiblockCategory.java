package net.astronomy.multilib.compat.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.compat.MultiblockPreviewPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REI display category for MultiLib multiblock structures. All layout/rendering/view-state logic
 * lives in the viewer-agnostic {@link MultiblockPreviewPanel}, shared identically with the JEI and
 * EMI integrations, so all three recipe browsers present the same GUI. This class only adapts REI's
 * specific API shape: {@link Widget} is a {@code GuiEventListener} directly, so a single widget owns
 * rendering, scrolling, dragging, and clicking together - no separate input-handler concept like
 * JEI's {@code IJeiInputHandler}.
 */
public class MultiblockCategory implements DisplayCategory<MultiblockDisplay> {

    static final CategoryIdentifier<MultiblockDisplay> ID =
            CategoryIdentifier.of("multilib", "multiblock_structure");

    // REI's Widgets.createRecipeBase(bounds) draws its own border/background INSIDE the same
    // bounds we're given, eating a few pixels off the top before our shared panel's title (which
    // starts flush at local y=0/TITLE_H) gets to draw - unlike JEI/EMI, which don't decorate our
    // bounds this way. Nudging our content down by a few px only for REI keeps the shared panel
    // (MultiblockPreviewPanel - used identically by JEI/EMI too) untouched. See PreviewWidget.render.
    private static final int CONTENT_Y_OFFSET = 5;

    private static final int WIDTH = 176;
    // Shorter than JEI's 296: REI's recipe-view screen spends extra vertical space above the
    // per-display area on its own chrome (the category name/arrows bar, plus a "Page X of Y" bar for
    // cycling between this category's displays) that JEI's recipes GUI doesn't reserve the same way.
    // At 296 the panel's own required-blocks list rendered past where REI's box actually ends,
    // overlapping that chrome instead of sitting inside the recipe box.
    private static final int HEIGHT = 240;

    private static final MultiblockPreviewPanel.Labels LABELS = new MultiblockPreviewPanel.Labels(
            Component.translatable("multilib.preview.layer_all"),
            Component.translatable("multilib.preview.required_blocks"));

    /**
     * Persistent per-definition state - survives layout re-creation while the REI recipe-view
     * screen stays open (so switching between recipes/categories keeps zoom etc.), but is reset
     * whenever that screen closes; see {@link ReiScreenResetHandler}.
     */
    private static final Map<ResourceLocation, MultiblockPreviewPanel.ViewState> STATES = new HashMap<>();

    static MultiblockPreviewPanel.ViewState state(MultiblockDefinition def) {
        return STATES.computeIfAbsent(def.getId(), k -> MultiblockPreviewPanel.newViewState(def));
    }

    /** Resets every definition's view state to defaults; called when the REI recipe-view screen closes. */
    static void resetAllViewStates() {
        STATES.clear();
    }

    @Override
    public CategoryIdentifier<MultiblockDisplay> getCategoryIdentifier() {
        return ID;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rei.multilib.category.multiblock_structure");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(MultiblockPreviewPanel.categoryIconStack());
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(MultiblockDisplay display) {
        return WIDTH;
    }

    @Override
    public List<Widget> setupDisplay(MultiblockDisplay display, Rectangle bounds) {
        MultiblockDefinition def = display.getData().definition();
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(new PreviewWidget(bounds, def));
        return widgets;
    }

    // ── Custom widget: thin adapter forwarding REI's native input events into MultiblockPreviewPanel ──

    private static final class PreviewWidget extends WidgetWithBounds {

        private final Rectangle bounds;
        private final MultiblockDefinition def;
        private final MultiblockPreviewPanel.ViewState vs;

        PreviewWidget(Rectangle bounds, MultiblockDefinition def) {
            this.bounds = bounds;
            this.def = def;
            this.vs = state(def);
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        private MultiblockPreviewPanel.Layout layout() {
            // Content is pushed down by CONTENT_Y_OFFSET (see render()) but REI still allocates the
            // full HEIGHT box for us - using the full HEIGHT here made the panel's own content bottom
            // land CONTENT_Y_OFFSET px past the actual bottom of REI's recipe box (the required-blocks
            // list spilling out below it). Shrinking the logical height by that same offset keeps the
            // rendered content flush with the real box: local content bottom (HEIGHT - OFFSET) plus the
            // OFFSET translate lands exactly at HEIGHT.
            return MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT - CONTENT_Y_OFFSET);
        }

        @Override
        public void render(GuiGraphics gfx, int mx, int my, float delta) {
            gfx.pose().pushPose();
            gfx.pose().translate(bounds.x, bounds.y + CONTENT_Y_OFFSET, 0);
            int localX = mx - bounds.x, localY = my - bounds.y - CONTENT_Y_OFFSET;
            MultiblockPreviewPanel.render(gfx, def, vs, layout(), LABELS, localX, localY);
            MultiblockPreviewPanel.tooltipAt(def, vs, layout(), localX, localY).ifPresent(target -> {
                Component text = switch (target) {
                    case MultiblockPreviewPanel.TooltipTarget.Title t -> Component.literal(t.fullName());
                    case MultiblockPreviewPanel.TooltipTarget.RotateBadge b -> Component.translatable(b.autoRotateOn()
                            ? "multilib.preview.auto_rotate.on" : "multilib.preview.auto_rotate.off");
                    case MultiblockPreviewPanel.TooltipTarget.ListRow row -> Component.literal(
                            "× " + row.count() + " " + row.stack().getHoverName().getString());
                };
                Tooltip.create(text).queue();
            });
            gfx.pose().popPose();
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sdx, double sdy) {
            return MultiblockPreviewPanel.onScroll(vs, layout(), def, mx - bounds.x, my - bounds.y - CONTENT_Y_OFFSET, sdy);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button != 0) return false;
            return MultiblockPreviewPanel.onDrag(vs, layout(), mx - bounds.x, my - bounds.y - CONTENT_Y_OFFSET, dx, dy);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) return false;
            MultiblockPreviewPanel.Layout lo = layout();
            double lx = mx - bounds.x, ly = my - bounds.y - CONTENT_Y_OFFSET;
            boolean consumed = MultiblockPreviewPanel.onClick(vs, lo, def, lx, ly);
            return MultiblockPreviewPanel.armPendingClick(vs, lo, lx, ly) || consumed;
        }

        // Resolves a pending model-click (see MultiblockPreviewPanel.onClick's model-click branch)
        // on actual mouse release rather than a fixed timer, so a slow-starting rotate-drag can never
        // have its cancelling effect on the pending click pre-empted by the click resolving first.
        // REI's Widget is a GuiEventListener (via AbstractContainerEventHandler/ContainerEventHandler),
        // which declares mouseReleased(double, double, int) as a default method - confirmed via javap
        // against RoughlyEnoughItems-api-neoforge-16.0.799.jar (Widget, WidgetWithBounds) and the
        // Minecraft 1.21.1 client (net.minecraft.client.gui.components.events.GuiEventListener /
        // ContainerEventHandler) - so overriding it here is safe and reaches this widget normally.
        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (button != 0) return false;
            return MultiblockPreviewPanel.resolveClickOnRelease(vs, layout(), def);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }
    }
}

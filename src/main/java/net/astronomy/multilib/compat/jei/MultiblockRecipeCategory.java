package net.astronomy.multilib.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.compat.MultiblockPreviewPanel;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * JEI display category for MultiLib multiblock structures. All layout/rendering/view-state logic
 * lives in the viewer-agnostic {@link MultiblockPreviewPanel}, shared identically with the REI and
 * EMI integrations (see {@code compat/rei/MultiblockCategory} / {@code compat/emi/MultiblockPreviewWidget})
 * so all three recipe browsers present the same GUI. This class only adapts JEI's specific API
 * shape: where the invisible input/output ingredients go (so JEI's "Uses"/"Recipes" lookups find
 * this category), how input events reach {@link MultiblockPreviewPanel#onScroll}/{@code onDrag}/
 * {@code onClick}, and how tooltips are built from {@link MultiblockPreviewPanel#tooltipAt}.
 */
public class MultiblockRecipeCategory implements IRecipeCategory<MultiblockRecipeDisplay> {

    static final RecipeType<MultiblockRecipeDisplay> RECIPE_TYPE =
            RecipeType.create("multilib", "multiblock_structure", MultiblockRecipeDisplay.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 296;

    private static final MultiblockPreviewPanel.Labels LABELS = new MultiblockPreviewPanel.Labels(
            Component.translatable("multilib.preview.layer_all"),
            Component.translatable("multilib.preview.required_blocks"));

    private final IDrawable icon;

    /**
     * Persistent per-definition state - survives layout re-creation while the JEI recipes GUI
     * stays open (so switching between recipes/categories keeps zoom etc.), but is reset whenever
     * the JEI recipes GUI screen is closed; see {@link JeiScreenResetHandler}.
     */
    private final Map<ResourceLocation, MultiblockPreviewPanel.ViewState> states = new HashMap<>();

    /** The single live instance, so the close-screen handler can reach {@link #states}. */
    private static MultiblockRecipeCategory instance;

    public MultiblockRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(MultiblockPreviewPanel.categoryIconStack());
        instance = this;
    }

    /** Resets every definition's view state to defaults; called when the JEI recipes GUI closes. */
    public static void resetAllViewStates() {
        if (instance != null) instance.states.clear();
    }

    private MultiblockPreviewPanel.ViewState state(MultiblockDefinition def) {
        return states.computeIfAbsent(def.getId(), k -> MultiblockPreviewPanel.newViewState(def));
    }

    // ── IRecipeCategory boilerplate ───────────────────────────────────────────

    @Override public RecipeType<MultiblockRecipeDisplay> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.multilib.category.multiblock_structure"); }
    @Override public int getWidth()  { return WIDTH;  }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockRecipeDisplay recipe, IFocusGroup focuses) {
        // Invisible inputs so JEI's "Uses" lookup works for every component block; invisible output
        // (the core/activation block alone) so right-click "Recipes" opens this specific recipe, not
        // the whole category. Both lists come from the shared MultiblockRecipeDisplay.of(...), so JEI/
        // REI/EMI all filter identically.
        if (!recipe.inputs().isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(recipe.inputs());
        }
        if (!recipe.outputs().isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(recipe.outputs());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiblockRecipeDisplay recipe, IFocusGroup focuses) {
        MultiblockDefinition def = recipe.definition();
        builder.addInputHandler(new InputHandler(state(def), def));
    }

    // ── Input handler - delegates entirely to MultiblockPreviewPanel ─────────────────────────────

    private static final class InputHandler implements IJeiInputHandler {

        private final MultiblockPreviewPanel.ViewState vs;
        private final MultiblockDefinition def;

        InputHandler(MultiblockPreviewPanel.ViewState vs, MultiblockDefinition def) {
            this.vs = vs;
            this.def = def;
        }

        @Override
        public net.minecraft.client.gui.navigation.ScreenRectangle getArea() {
            return new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, WIDTH, HEIGHT);
        }

        @Override
        public boolean handleMouseScrolled(double mx, double my, double sdx, double sdy) {
            MultiblockPreviewPanel.Layout lo = MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT);
            return MultiblockPreviewPanel.onScroll(vs, lo, def, mx, my, sdy);
        }

        @Override
        public boolean handleMouseDragged(double mx, double my, InputConstants.Key mouseKey, double dx, double dy) {
            if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0) return false;
            MultiblockPreviewPanel.Layout lo = MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT);
            return MultiblockPreviewPanel.onDrag(vs, lo, mx, my, dx, dy);
        }

        // JEI calls handleInput(...) TWICE per left-click: once on mouse press with
        // input.isSimulate()==true (a probe to find which handler wants the click - see JEI's
        // UserInputRouter.handleSimulateClick / CombinedInputHandler.handleClickInternal, decompiled
        // from jei-1.21.1-neoforge-19.27.0.340.jar), and again on mouse *release* with
        // input.isSimulate()==false to actually commit (handleExecuteClick), but only against the
        // same handler that answered true on the simulate pass - confirmed via
        // ForgeUserInput.fromEvent(ScreenEvent.MouseButtonPressed) building InputType.SIMULATE and
        // fromEvent(ScreenEvent.MouseButtonReleased) building InputType.EXECUTE. So the non-simulate
        // call already *is* JEI's real mouse-release notification: no separate release hook exists on
        // IJeiInputHandler/IJeiUserInput (only getKey()/getModifiers()/isSimulate()/is(...)), but none
        // is needed here. Drags are routed through JEI's separate DragRouter (handleMouseDragged,
        // below) which fires from mouse-move-while-down, so by the time this release call arrives any
        // real drag has already cleared vs.pendingClick via onDrag. That means the non-simulate branch
        // can resolve the pending click immediately instead of deferring to a timer.
        @Override
        public boolean handleInput(double mx, double my, IJeiUserInput input) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0) return false;
            MultiblockPreviewPanel.Layout lo = MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT);
            boolean consumed = MultiblockPreviewPanel.onClick(vs, lo, def, mx, my, input.isSimulate());
            if (input.isSimulate()) {
                // Press probe: arm a pending model click here (NOT on the real/release call below -
                // re-arming there would erase whatever onDrag already cancelled in between, see
                // armPendingClick's javadoc).
                consumed = MultiblockPreviewPanel.armPendingClick(vs, lo, mx, my) || consumed;
            } else {
                // Real release: resolve whatever click is still pending (no-op if onDrag already
                // cancelled it, or if this release wasn't over the model at all).
                consumed = MultiblockPreviewPanel.resolveClickOnRelease(vs, lo, def) || consumed;
            }
            return consumed;
        }
    }

    // ── draw() ────────────────────────────────────────────────────────────────

    @Override
    public void draw(MultiblockRecipeDisplay recipe, IRecipeSlotsView slotsView,
                     GuiGraphics gfx, double mx, double my) {
        MultiblockDefinition def = recipe.definition();
        MultiblockPreviewPanel.ViewState vs = state(def);
        MultiblockPreviewPanel.Layout lo = MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT);
        MultiblockPreviewPanel.render(gfx, def, vs, lo, LABELS, (int) mx, (int) my);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void getTooltip(ITooltipBuilder tooltip, MultiblockRecipeDisplay recipe,
                           IRecipeSlotsView slotsView, double mx, double my) {
        MultiblockDefinition def = recipe.definition();
        MultiblockPreviewPanel.ViewState vs = state(def);
        MultiblockPreviewPanel.Layout lo = MultiblockPreviewPanel.layout(def, WIDTH, HEIGHT);
        MultiblockPreviewPanel.tooltipAt(def, vs, lo, mx, my).ifPresent(target -> {
            switch (target) {
                case MultiblockPreviewPanel.TooltipTarget.Title t ->
                        tooltip.add(Component.literal(t.fullName()));
                case MultiblockPreviewPanel.TooltipTarget.RotateBadge b ->
                        tooltip.add(Component.translatable(b.autoRotateOn()
                                ? "multilib.preview.auto_rotate.on" : "multilib.preview.auto_rotate.off"));
                case MultiblockPreviewPanel.TooltipTarget.ListRow row ->
                        tooltip.add(Component.literal("× " + row.count() + " " + row.stack().getHoverName().getString()));
            }
        });
    }
}

package net.astronomy.multilib.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import com.mojang.blaze3d.systems.RenderSystem;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.client.ClientConfig;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.astronomy.multilib.client.render.MultiblockStructurePreviewRenderer;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MultiblockRecipeCategory implements IRecipeCategory<MultiblockRecipeDisplay> {

    static final RecipeType<MultiblockRecipeDisplay> RECIPE_TYPE =
            RecipeType.create("multilib", "multiblock_structure", MultiblockRecipeDisplay.class);

    // ── Layout (recipe-local coordinates) ────────────────────────────────────

    private static final int WIDTH  = 176;
    private static final int HEIGHT = 296;

    // Title row at the very top: the multiblock's name, truncated with a tooltip if too long.
    private static final int TITLE_Y = 2;
    private static final int TITLE_H = 12;

    // Everything below the title splits into a model section (top) and a block-list section
    // (bottom). MODEL_AREA_RATIO is the single knob controlling that split — edit this constant to
    // change the proportion (e.g. 0.5f for an even split, 0.75f to give the model 3/4 of the space).
    private static final float MODEL_AREA_RATIO = 2f / 3f;

    private static final int CONTENT_TOP    = TITLE_Y + TITLE_H + 2;
    private static final int CONTENT_BOTTOM = HEIGHT - 2;
    private static final int CONTENT_H      = CONTENT_BOTTOM - CONTENT_TOP;
    private static final int MODEL_SECTION_H = Math.round(CONTENT_H * MODEL_AREA_RATIO);
    private static final int LIST_SECTION_TOP = CONTENT_TOP + MODEL_SECTION_H;
    private static final int LIST_SECTION_H   = CONTENT_BOTTOM - LIST_SECTION_TOP;

    // Layer navigation row sits at the bottom of the model section; the 3D preview fills the rest.
    private static final int LR_H  = 14;
    private static final int LR_Y  = CONTENT_TOP + MODEL_SECTION_H - LR_H;
    private static final int ARR_W = 20;

    private static final int P_X1 = 4;
    private static final int P_Y1 = CONTENT_TOP;
    private static final int P_X2 = 172;
    private static final int P_Y2 = LR_Y - 2;
    private static final int P_CX = (P_X1 + P_X2) / 2;
    private static final int P_CY = (P_Y1 + P_Y2) / 2;
    private static final int P_SZ = P_X2 - P_X1;
    private static final int ARR_L = P_X1;           // left arrow x start
    private static final int ARR_R = P_X2 - ARR_W;   // right arrow x start

    // 3D preview's actual clipped/scissored area: inset a few px from the preview bounds above, never
    // lower than the layer-nav row's top edge.
    private static final int MODEL_AREA_MARGIN = 2;
    private static final int MODEL_TOP    = P_Y1 + MODEL_AREA_MARGIN;
    private static final int MODEL_BOTTOM = LR_Y - MODEL_AREA_MARGIN;
    private static final int MODEL_LEFT   = P_X1 + MODEL_AREA_MARGIN;
    private static final int MODEL_RIGHT  = P_X2 - MODEL_AREA_MARGIN;

    // Rotate-state button — a 16x16 badge overlaid ON TOP of the model (drawn after it, with scissor
    // already disabled) rather than a reserved header row. Still clickable (toggles the persistent
    // preference) on top of the temporary drag-pause behavior.
    private static final int AUTO_W = 16;
    private static final int AUTO_H = 16;
    private static final int AUTO_X = MODEL_RIGHT - AUTO_W - 1;
    private static final int AUTO_Y = MODEL_TOP + 1;

    // Selected-block name+icon badge — same overlay row as the rotate button, opposite (top-left) corner.
    private static final int SEL_X = MODEL_LEFT + 1;
    private static final int SEL_Y = MODEL_TOP + 1;
    private static final int SEL_H = 16;
    private static final int SEL_LABEL_MAX_W = 70;

    // Required-blocks list
    private static final int ROW_H   = 18;
    private static final int SB_W    = 6;                       // scrollbar width — vanilla widget/scroller sprite is natively 6px wide
    private static final int LBL_Y   = LIST_SECTION_TOP + 3;
    private static final int LIST_Y  = LBL_Y + 12;
    private static final int LIST_H  = LIST_SECTION_TOP + LIST_SECTION_H - LIST_Y - 2;
    private static final int MAX_VIS = LIST_H / ROW_H;

    // ── Rotation / animation ──────────────────────────────────────────────────

    private static final float DEFAULT_PITCH    = 22f;
    private static final float DRAG_SENSITIVITY = 0.5f;
    private static final float ZOOM_STEP        = 0.1f;
    private static final float ZOOM_MIN         = 0.3f;
    private static final float ZOOM_MAX         = 3.0f;
    private static final float AUTO_YAW_MS      = 360f / 8000f; // full cycle in 8 s

    // ── State ─────────────────────────────────────────────────────────────────

    private final IDrawable icon;
    /**
     * Persistent per-definition state — survives layout re-creation while the JEI recipes GUI
     * stays open (so switching between recipes/categories keeps zoom etc.), but is reset whenever
     * the JEI recipes GUI screen is closed; see {@link net.astronomy.multilib.compat.jei.JeiScreenResetHandler}.
     */
    private final Map<ResourceLocation, ViewState> states = new HashMap<>();

    /** The single live instance, so the close-screen handler can reach {@link #states}. */
    private static MultiblockRecipeCategory instance;

    private static ItemStack categoryIconStack() {
        String id = net.astronomy.multilib.client.ClientConfig.CATEGORY_ICON.get();
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc != null) {
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
            if (item != null) return new ItemStack(item);
        }
        return new ItemStack(Items.STRUCTURE_BLOCK);
    }

    public MultiblockRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(categoryIconStack());
        instance = this;
    }

    /** Resets every definition's view state to defaults; called when the JEI recipes GUI closes. */
    public static void resetAllViewStates() {
        if (instance != null) instance.states.clear();
    }

    static final class ViewState {
        float   yaw          = 0f;
        float   pitch        = DEFAULT_PITCH;
        float   zoom         = 1.0f;
        Integer layer        = null;  // null = show all layers
        int     scroll       = 0;     // first visible index in the required-blocks list
        /** Last time the player dragged the model; auto-rotate is paused while this is recent. */
        long    lastDragTime = 0L;
        /** Block currently picked/highlighted on the model, or null. */
        MultiblockStructurePreviewRenderer.BlockHit selectedHit = null;
        final TickerState titleTicker = new TickerState();
        final TickerState labelTicker = new TickerState();
    }

    /** Per-text scroll state for {@link #drawTicker}: accumulated scroll time, only while not hovered. */
    private static final class TickerState {
        long scrollMs = 0L;
        long lastFrameNanos = 0L;
    }

    /** Auto-rotate is paused for this long after the last drag input, then resumes on its own. */
    private static final long DRAG_PAUSE_MS = 500L;

    /** The yaw actually used to render this frame — animated if auto-rotating, fixed otherwise. */
    private static float effectiveYaw(ViewState vs) {
        boolean dragPaused = System.currentTimeMillis() - vs.lastDragTime < DRAG_PAUSE_MS;
        boolean autoRotate = ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get() && !dragPaused;
        return autoRotate
                ? (System.currentTimeMillis() % 8000L) * AUTO_YAW_MS
                : vs.yaw;
    }

    private static boolean isEffectiveAutoRotate(ViewState vs) {
        boolean dragPaused = System.currentTimeMillis() - vs.lastDragTime < DRAG_PAUSE_MS;
        return ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get() && !dragPaused;
    }

    private ViewState state(MultiblockDefinition def) {
        return states.computeIfAbsent(def.getId(), k -> new ViewState());
    }

    // ── IRecipeCategory boilerplate ───────────────────────────────────────────

    @Override public RecipeType<MultiblockRecipeDisplay> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.multilib.category.multiblock_structure"); }
    @Override public int getWidth()  { return WIDTH;  }
    @Override public int getHeight() { return HEIGHT; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockRecipeDisplay recipe, IFocusGroup focuses) {
        MultiblockDefinition def = recipe.definition();

        // Invisible inputs so JEI's "Uses" lookup works for every component block.
        List<ItemStack> allStacks = new ArrayList<>();
        for (BlockIngredient ing : def.getBlockMap().values()) {
            ItemStack s = representativeStack(ing);
            if (!s.isEmpty()) allStacks.add(s);
        }
        if (!allStacks.isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(allStacks);
        }

        // Controller as OUTPUT so "Recipes" right-click on it opens this category.
        char actSym = def.getActivationSymbol();
        char corSym = def.getCoreSymbol();
        char disSym = actSym != '\0' ? actSym : corSym;
        if (disSym != '\0') {
            BlockIngredient ing = def.getBlockMap().get(disSym);
            if (ing != null) {
                ItemStack s = representativeStack(ing);
                if (!s.isEmpty())
                    builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(List.of(s));
            }
        }

        // Real ingredient slots for every required-blocks legend row, tagged CATALYST so they're
        // distinguishable from the invisible INPUT/OUTPUT lookup slots above. There is one slot per
        // distinct block type (not capped), each clickable for JEI's "Uses"/"Recipes" lookup.
        // Positions set here are placeholders; LegendWidget repositions the MAX_VIS currently-visible
        // slots into the list rows each frame based on the per-definition scroll offset, and parks
        // the rest off-screen.
        List<Map.Entry<ItemStack, Integer>> items = countItems(def);
        for (Map.Entry<ItemStack, Integer> item : items) {
            builder.addSlot(RecipeIngredientRole.CATALYST, P_X1 + 1, LIST_Y + 1)
                    .setStandardSlotBackground()
                    .addItemStack(item.getKey());
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiblockRecipeDisplay recipe, IFocusGroup focuses) {
        MultiblockDefinition def = recipe.definition();
        List<IRecipeSlotDrawable> legendSlots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.CATALYST);
        ViewState vs = state(def);
        builder.addInputHandler(new InputHandler(vs, def, legendSlots));
        builder.addSlottedWidget(new LegendWidget(vs, def, legendSlots), legendSlots);
    }

    /**
     * Owns the real JEI ingredient slots for the required-blocks legend. Every distinct block type
     * gets a slot (added invisibly off-list in setRecipe); each frame this widget repositions the
     * MAX_VIS slots that fall within the current scroll window into the visible list rows and parks
     * the rest off-screen, then draws the count badge, name label, and a functional scrollbar (mouse
     * wheel + drag/click, using the SB_W-wide reserved area) so all entries — not just the first
     * MAX_VIS — stay reachable and clickable.
     */
    private static final class LegendWidget implements ISlottedRecipeWidget {

        private final ViewState vs;
        private final MultiblockDefinition def;
        private final List<IRecipeSlotDrawable> legendSlots;

        LegendWidget(ViewState vs, MultiblockDefinition def, List<IRecipeSlotDrawable> legendSlots) {
            this.vs = vs;
            this.def = def;
            this.legendSlots = legendSlots;
        }

        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(0, 0);
        }

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            int visible = Math.min(MAX_VIS, legendSlots.size() - vs.scroll);
            for (int i = 0; i < visible; i++) {
                IRecipeSlotDrawable slot = legendSlots.get(vs.scroll + i);
                if (slot.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(new RecipeSlotUnderMouse(slot, 0, 0));
                }
            }
            return Optional.empty();
        }

        @Override
        public void drawWidget(GuiGraphics gfx, double mouseX, double mouseY) {
            int total = legendSlots.size();
            int maxScroll = Math.max(0, total - MAX_VIS);
            vs.scroll = Math.max(0, Math.min(maxScroll, vs.scroll));
            int visible = Math.min(MAX_VIS, total - vs.scroll);

            Font font = Minecraft.getInstance().font;
            List<Map.Entry<ItemStack, Integer>> items = countItems(def);
            int listRight = WIDTH - SB_W - 3;

            for (int i = 0; i < total; i++) {
                IRecipeSlotDrawable slot = legendSlots.get(i);
                int visIdx = i - vs.scroll;
                if (visIdx < 0 || visIdx >= MAX_VIS) {
                    slot.setPosition(P_X1 + 1, -1000);
                    continue;
                }
                int rowY = LIST_Y + visIdx * ROW_H;
                slot.setPosition(P_X1 + 1, rowY + 1);
                slot.draw(gfx);
                if (slot.isMouseOver(mouseX, mouseY)) slot.drawHoverOverlays(gfx);

                Map.Entry<ItemStack, Integer> e = items.get(i);
                int count = e.getValue();
                if (count > 1) {
                    // Bottom-right of the 16x16 slot icon, full vanilla stack-count size — the slot
                    // itself is already vanilla's exact 18x18 size (setStandardSlotBackground()), so
                    // matching vanilla's own count scale here keeps the two visually consistent.
                    String label = String.valueOf(count);
                    float textScale = 1.0f;
                    int iconRight = P_X1 + 1 + 16;
                    int iconBottom = rowY + 1 + 16;
                    gfx.pose().pushPose();
                    gfx.pose().translate(iconRight - 1, iconBottom - 1, 200);
                    gfx.pose().scale(textScale, textScale, 1f);
                    gfx.drawString(font, label, -font.width(label), -font.lineHeight, 0xFFFFFF, true);
                    gfx.pose().popPose();
                }

                String name = e.getKey().getHoverName().getString();
                int maxNameW = listRight - (P_X1 + 20) - 2;
                if (font.width(name) > maxNameW) {
                    while (name.length() > 1 && font.width(name + "…") > maxNameW)
                        name = name.substring(0, name.length() - 1);
                    name += "…";
                }
                gfx.drawString(font, name, P_X1 + 20, rowY + (ROW_H - font.lineHeight) / 2, 0x303030, false);
            }

            // Scrollbar: only drawn/functional when there are more entries than fit. Uses vanilla's
            // own scrollbar widget sprites (the same ones used by e.g. the options/control lists) so
            // it matches the game's native look instead of a flat-color bar.
            if (maxScroll > 0) {
                int sbX = WIDTH - SB_W - 2;
                int thumbH = Math.max(8, LIST_H * MAX_VIS / total);
                int thumbY = LIST_Y + (LIST_H - thumbH) * vs.scroll / maxScroll;
                gfx.blitSprite(SCROLLER_BACKGROUND_SPRITE, sbX, LIST_Y, SB_W, LIST_H);
                gfx.blitSprite(SCROLLER_SPRITE, sbX, thumbY, SB_W, thumbH);
            }
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            int visible = Math.min(MAX_VIS, legendSlots.size() - vs.scroll);
            for (int i = 0; i < visible; i++) {
                int rowY = LIST_Y + i * ROW_H;
                if (mouseX >= P_X1 + 20 && mouseX < WIDTH - SB_W && mouseY >= rowY && mouseY < rowY + ROW_H) {
                    List<Map.Entry<ItemStack, Integer>> items = countItems(def);
                    tooltip.add(Component.literal("× " + items.get(vs.scroll + i).getValue()));
                    return;
                }
            }
        }
    }

    // ── Input handler ─────────────────────────────────────────────────────────

    private static final class InputHandler implements IJeiInputHandler {

        private final ViewState vs;
        private final MultiblockDefinition def;
        private final int nLayers;
        private final List<IRecipeSlotDrawable> legendSlots;

        InputHandler(ViewState vs, MultiblockDefinition def, List<IRecipeSlotDrawable> legendSlots) {
            this.vs = vs;
            this.def = def;
            this.nLayers = def.getLayerCount();
            this.legendSlots = legendSlots;
        }

        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, 0, WIDTH, HEIGHT);
        }

        @Override
        public boolean handleMouseScrolled(double mx, double my, double sdx, double sdy) {
            // Scroll over preview → zoom
            if (mx >= P_X1 && mx <= P_X2 && my >= P_Y1 && my <= P_Y2) {
                vs.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, vs.zoom + (sdy > 0 ? ZOOM_STEP : -ZOOM_STEP)));
                return true;
            }
            // Scroll over the required-blocks list → scroll through all entries
            if (mx >= P_X1 && mx <= WIDTH - 2 && my >= LIST_Y && my <= LIST_Y + LIST_H) {
                int maxScroll = Math.max(0, legendSlots.size() - MAX_VIS);
                vs.scroll = Math.max(0, Math.min(maxScroll, vs.scroll + (sdy > 0 ? -1 : 1)));
                return true;
            }
            return false;
        }

        @Override
        public boolean handleMouseDragged(double mx, double my, InputConstants.Key mouseKey, double dx, double dy) {
            if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0) return false;
            // Dragging the scrollbar thumb/track scrolls the required-blocks list.
            int scrollbarX = WIDTH - SB_W - 2;
            if (mx >= scrollbarX && mx <= WIDTH - 2 && my >= LIST_Y && my <= LIST_Y + LIST_H) {
                scrollToMouseY(my);
                return true;
            }
            // Capture the currently-animated yaw so the model doesn't jump when auto-rotate pauses.
            if (System.currentTimeMillis() - vs.lastDragTime >= DRAG_PAUSE_MS) {
                vs.yaw = (System.currentTimeMillis() % 8000L) * AUTO_YAW_MS;
            }
            vs.yaw  += (float) dx * DRAG_SENSITIVITY;
            vs.pitch = Math.max(-80f, Math.min(80f, vs.pitch + (float) dy * DRAG_SENSITIVITY));
            // Manual drag only pauses auto-rotate while actively dragging — no persistent toggle; it
            // resumes on its own DRAG_PAUSE_MS after the player stops (there's no mouse-released event
            // in JEI's IJeiInputHandler to detect release directly, so a short idle timeout substitutes).
            vs.lastDragTime = System.currentTimeMillis();
            return true;
        }

        private void scrollToMouseY(double my) {
            int maxScroll = Math.max(0, legendSlots.size() - MAX_VIS);
            if (maxScroll == 0) { vs.scroll = 0; return; }
            double t = (my - LIST_Y) / (double) LIST_H;
            vs.scroll = Math.max(0, Math.min(maxScroll, (int) Math.round(t * maxScroll)));
        }

        @Override
        public boolean handleInput(double mx, double my, IJeiUserInput input) {
            if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0) return false;

            // Rotate-state button: click to toggle the persistent auto-rotate preference.
            if (mx >= AUTO_X && mx < AUTO_X + AUTO_W && my >= AUTO_Y && my < AUTO_Y + AUTO_H) {
                if (!input.isSimulate()) {
                    boolean current = ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get();
                    if (current) {
                        vs.yaw = effectiveYaw(vs); // freeze in place when turning auto-rotate off
                    }
                    ClientConfig.JEI_PREVIEW_AUTO_ROTATE.set(!current);
                }
                return true;
            }

            // Scrollbar track click → jump-scroll the required-blocks list
            int scrollbarX = WIDTH - SB_W - 2;
            if (mx >= scrollbarX && mx <= WIDTH - 2 && my >= LIST_Y && my <= LIST_Y + LIST_H) {
                if (!input.isSimulate()) scrollToMouseY(my);
                return true;
            }

            // Click on the model → pick the block under the cursor, highlight it, show its name.
            // Clicking the same block again (or empty space) clears the selection.
            if (mx >= MODEL_LEFT && mx < MODEL_RIGHT && my >= MODEL_TOP && my < MODEL_BOTTOM) {
                if (!input.isSimulate()) {
                    Integer onlyLayerIndex = vs.layer == null ? null : (nLayers - 1 - vs.layer);
                    MultiblockStructurePreviewRenderer.BlockHit hit = MultiblockStructurePreviewRenderer.pick(
                            def, P_CX, P_CY, Math.round(P_SZ * vs.zoom), effectiveYaw(vs), vs.pitch, onlyLayerIndex, mx, my);
                    vs.selectedHit = (hit != null && hit.equals(vs.selectedHit)) ? null : hit;
                }
                return true;
            }

            // Layer arrows (only when multiple layers exist)
            if (nLayers > 1 && my >= LR_Y && my < LR_Y + LR_H) {
                boolean hitLeft  = mx >= ARR_L && mx < ARR_L + ARR_W;
                boolean hitRight = mx >= ARR_R && mx < ARR_R + ARR_W;
                if (hitLeft || hitRight) {
                    if (!input.isSimulate()) {
                        // Nested ternaries with mixed Integer/int branches force javac to unify
                        // to int, which unboxes the "null" branch at runtime → NPE. Use if/else.
                        if (hitLeft) {
                            // null → last → last-1 → … → 0 → null (wrap-around)
                            if (vs.layer == null) vs.layer = nLayers - 1;
                            else if (vs.layer == 0) vs.layer = null;
                            else vs.layer = vs.layer - 1;
                        } else {
                            // null → 0 → 1 → … → last → null (wrap-around)
                            if (vs.layer == null) vs.layer = 0;
                            else if (vs.layer >= nLayers - 1) vs.layer = null;
                            else vs.layer = vs.layer + 1;
                        }
                    }
                    return true;
                }
            }

            return false;
        }
    }

    // ── draw() ────────────────────────────────────────────────────────────────

    @Override
    public void draw(MultiblockRecipeDisplay recipe, IRecipeSlotsView slotsView,
                     GuiGraphics gfx, double mx, double my) {
        MultiblockDefinition def = recipe.definition();
        ViewState vs = state(def);
        Font font = Minecraft.getInstance().font;

        // ── Title row: the multiblock's name. If it fits, just centered. If not, it auto-scrolls as a
        // looping ticker, paused while the title row is hovered (mouse stays put on the part you want
        // to read). The tooltip in getTooltip() still shows the full name too, as a backup.
        String title = multiblockName(def);
        int maxTitleW = WIDTH - 2 * P_X1;
        boolean hoveringTitle = mx >= 0 && mx < WIDTH && my >= TITLE_Y && my < TITLE_Y + TITLE_H;
        if (font.width(title) <= maxTitleW) {
            gfx.drawCenteredString(font, title, WIDTH / 2, TITLE_Y + (TITLE_H - font.lineHeight) / 2, 0x404040);
        } else {
            drawTicker(gfx, font, title, P_X1, TITLE_Y + (TITLE_H - font.lineHeight) / 2, maxTitleW,
                    0x404040, hoveringTitle, vs.titleTicker);
        }

        // ── 3D model ──────────────────────────────────────────────────────────
        int nLayers = def.getLayerCount();
        // vs.layer counts up from the bottom (0 = bottommost, displayed "1/N") while the definition's
        // own layer array is declared top-to-bottom (index 0 = topmost) — convert here.
        Integer onlyLayerIndex = vs.layer == null ? null : (nLayers - 1 - vs.layer);
        boolean persistentAutoRotate = ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get();
        boolean effectiveAutoRotate = isEffectiveAutoRotate(vs);
        float yaw = effectiveYaw(vs);
        // Clip to MODEL_* so a zoomed-in model can never paint outside its designated area (e.g. over
        // the layer-nav row below it). enableScissor uses absolute window coordinates, not
        // pose-stack-relative ones like fill()/blit() do, so the recipe-local corners have to be run
        // through the current pose matrix first.
        int[] scissorMin = toAbsoluteScreen(gfx, MODEL_LEFT, MODEL_TOP);
        int[] scissorMax = toAbsoluteScreen(gfx, MODEL_RIGHT, MODEL_BOTTOM);
        gfx.enableScissor(scissorMin[0], scissorMin[1], scissorMax[0], scissorMax[1]);
        MultiblockStructurePreviewRenderer.render(
                gfx, def, P_CX, P_CY, Math.round(P_SZ * vs.zoom), yaw, vs.pitch, onlyLayerIndex, vs.selectedHit);
        gfx.disableScissor();

        // Everything from here on must render strictly in front of the model, regardless of zoom —
        // the model's buffered block draws flush at a different point than GuiGraphics' own 2D draws,
        // so a plain "drawn later in code" order doesn't reliably win the depth test once a large
        // zoomed-in block is in front of these overlays. Pushing them to a clearly-nearer Z forces it.
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 300);

        // ── Rotate-state button (overlaid on top of the model, no scissor) — vanilla button sprite,
        // square. The red "off" bar only reflects the persistent preference, not a temporary
        // drag-pause: a drag-pause shouldn't read as "rotation disabled".
        boolean hoverAuto = mx >= AUTO_X && mx < AUTO_X + AUTO_W && my >= AUTO_Y && my < AUTO_Y + AUTO_H;
        gfx.blitSprite(hoverAuto ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE, AUTO_X, AUTO_Y, AUTO_W, AUTO_H);
        drawRotateIcon(gfx, AUTO_X, AUTO_Y, AUTO_W, persistentAutoRotate);

        // ── Selected-block name+icon badge, top-left corner of the model, same overlay row. The name
        // is capped to SEL_LABEL_MAX_W and auto-scrolls (same ticker as the title) if it's longer. ──
        if (vs.selectedHit != null) {
            BlockIngredient selIng = def.getBlockMap().get(vs.selectedHit.symbol());
            if (selIng != null) {
                ItemStack selStack = representativeStack(selIng);
                if (!selStack.isEmpty()) {
                    String label = selStack.getHoverName().getString();
                    int iconW = 16;
                    int padX = 4;
                    int labelW = Math.min(font.width(label), SEL_LABEL_MAX_W);
                    int badgeW = iconW + padX + labelW + padX;
                    gfx.blitSprite(BUTTON_SPRITE, SEL_X, SEL_Y, badgeW, SEL_H);
                    gfx.renderItem(selStack, SEL_X + 1, SEL_Y);
                    int labelX = SEL_X + iconW + padX;
                    int labelY = SEL_Y + (SEL_H - font.lineHeight) / 2;
                    boolean hoveringSel = mx >= SEL_X && mx < SEL_X + badgeW && my >= SEL_Y && my < SEL_Y + SEL_H;
                    if (font.width(label) <= labelW) {
                        gfx.drawString(font, label, labelX, labelY, 0xFFFFFF, false);
                    } else {
                        drawTicker(gfx, font, label, labelX, labelY, labelW, 0xFFFFFF, hoveringSel, vs.labelTicker);
                    }
                }
            }
        }

        // ── Layer navigation ──────────────────────────────────────────────────
        if (nLayers > 1) {
            String layerLabel = vs.layer == null
                    ? Component.translatable("jei.multilib.layer.all").getString()
                    : (vs.layer + 1) + " / " + nLayers;
            int labelColor = vs.layer == null ? 0x505050 : 0x2060C8;

            boolean hoverL = mx >= ARR_L && mx < ARR_L + ARR_W && my >= LR_Y && my < LR_Y + LR_H;
            boolean hoverR = mx >= ARR_R && mx < ARR_R + ARR_W && my >= LR_Y && my < LR_Y + LR_H;

            gfx.fill(ARR_L, LR_Y, ARR_L + ARR_W, LR_Y + LR_H, hoverL ? 0xFF909090 : 0xFF707070);
            gfx.drawCenteredString(font, "◀", ARR_L + ARR_W / 2, LR_Y + (LR_H - font.lineHeight) / 2, 0xFFFFFF);

            gfx.fill(ARR_R, LR_Y, ARR_R + ARR_W, LR_Y + LR_H, hoverR ? 0xFF909090 : 0xFF707070);
            gfx.drawCenteredString(font, "▶", ARR_R + ARR_W / 2, LR_Y + (LR_H - font.lineHeight) / 2, 0xFFFFFF);

            gfx.drawCenteredString(font, layerLabel, WIDTH / 2, LR_Y + (LR_H - font.lineHeight) / 2, labelColor);
        }

        // ── "Required blocks:" label ──────────────────────────────────────────
        gfx.drawString(font, Component.translatable("jei.multilib.required_blocks").getString(),
                P_X1, LBL_Y, 0x404040, false);

        gfx.pose().popPose();

        // The list itself (slots, count/name labels, scrollbar) is drawn by LegendWidget,
        // registered in createRecipeExtras as an ISlottedRecipeWidget.
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void getTooltip(ITooltipBuilder tooltip, MultiblockRecipeDisplay recipe,
                           IRecipeSlotsView slotsView, double mx, double my) {
        // Title row: full name, in case it was truncated.
        if (mx >= 0 && mx < WIDTH && my >= TITLE_Y && my < TITLE_Y + TITLE_H) {
            tooltip.add(Component.literal(multiblockName(recipe.definition())));
            return;
        }

        // Rotate-state button: click to toggle, drag the model to rotate it manually.
        if (mx >= AUTO_X && mx < AUTO_X + AUTO_W && my >= AUTO_Y && my < AUTO_Y + AUTO_H) {
            tooltip.add(Component.translatable(ClientConfig.JEI_PREVIEW_AUTO_ROTATE.get()
                    ? "jei.multilib.auto_rotate.on"
                    : "jei.multilib.auto_rotate.off"));
            return;
        }

        // Item rows: handled by LegendWidget.getTooltip (it owns scroll state/positions).
    }

    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    // Vanilla's own button background (the same beveled sprite as any in-game button) — nine-patch,
    // so blitSprite stretches it cleanly to any square/rectangular size.
    private static final ResourceLocation BUTTON_SPRITE = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private static final ResourceLocation ROTATE_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "textures/gui/rotate_icon.png");

    /**
     * Draws the square rotate/refresh-arrow icon texture (32x32 source, downscaled to fit the
     * button) at the top-left corner (x, y) of a {@code size}x{@code size} square. When disabled,
     * overlays a red diagonal "forbidden" bar from corner to corner instead of dimming the icon.
     */
    private static void drawRotateIcon(GuiGraphics gfx, int x, int y, int size, boolean enabled) {
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        float scale = size / 16f;
        gfx.pose().scale(scale, scale, 1f);
        if (!enabled) RenderSystem.setShaderColor(0.75f, 0.75f, 0.75f, 1f);
        gfx.blit(ROTATE_ICON_TEXTURE, 0, 0, 0, 0, 16, 16, 16, 16);
        if (!enabled) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gfx.pose().popPose();

        if (!enabled) {
            // Bottom-left to top-right diagonal "forbidden" bar.
            int thickness = Math.max(2, size / 6);
            int steps = size * 2;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                int px = x + Math.round(t * size);
                int py = y + size - Math.round(t * size);
                gfx.fill(px - thickness / 2, py - thickness / 2, px + thickness / 2 + 1, py + thickness / 2 + 1, 0xFFC01A1A);
            }
        }
    }

    /**
     * Draws {@code text} as a looping horizontal ticker clipped to {@code maxWidth}, for text too
     * long to fit. Scroll only advances while NOT {@code hovering}, so mousing over it holds still.
     * Caller must already have checked {@code font.width(text) > maxWidth} — this always scrolls.
     */
    private static void drawTicker(GuiGraphics gfx, Font font, String text, int x, int y, int maxWidth,
                                   int color, boolean hovering, TickerState ticker) {
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

    /** Transforms a recipe-local coordinate through the current pose stack into absolute window pixels. */
    private static int[] toAbsoluteScreen(GuiGraphics gfx, int x, int y) {
        org.joml.Vector3f v = new org.joml.Vector3f(x, y, 0);
        v.mulPosition(gfx.pose().last().pose());
        return new int[]{Math.round(v.x), Math.round(v.y)};
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Counts occurrences of every block symbol across all layers. Sorted so the structure's core
     * block ranks first, IO-port blocks rank next, and everything else follows by count descending
     * (count descending is also the tiebreak within the core/io-port/normal groups).
     */
    private static List<Map.Entry<ItemStack, Integer>> countItems(MultiblockDefinition def) {
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

    private static ItemStack representativeStack(BlockIngredient ingredient) {
        for (Block block : ingredient.getCandidateBlocks()) {
            Item item = block.asItem();
            if (item != Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    /** The multiblock's "name", for the title row — the core/activation block's own display name. */
    private static String multiblockName(MultiblockDefinition def) {
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

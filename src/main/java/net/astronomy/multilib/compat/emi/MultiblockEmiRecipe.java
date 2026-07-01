package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.stream.Collectors;

/**
 * EMI recipe entry for a MultiLib multiblock structure.
 * {@link EmiRecipe} is an interface in EMI 1.1+; all methods are implemented here.
 */
public class MultiblockEmiRecipe implements EmiRecipe {

    static EmiRecipeCategory CATEGORY = null;

    static EmiRecipeCategory getOrCreateCategory() {
        if (CATEGORY == null) {
            String id = net.astronomy.multilib.client.ClientConfig.CATEGORY_ICON.get();
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(id);
            net.minecraft.world.item.Item item = loc != null
                    ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc).orElse(null)
                    : null;
            ItemStack iconStack = item != null ? new ItemStack(item) : new ItemStack(Items.STRUCTURE_BLOCK);
            CATEGORY = new EmiRecipeCategory(
                    ResourceLocation.fromNamespaceAndPath("multilib", "multiblock_structure"),
                    EmiStack.of(iconStack)
            );
        }
        return CATEGORY;
    }

    private final MultiblockRecipeDisplay data;

    public MultiblockEmiRecipe(MultiblockRecipeDisplay data) {
        this.data = data;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return getOrCreateCategory();
    }

    @Override
    public ResourceLocation getId() {
        // EMI requires synthetic recipes (not in the vanilla recipe manager) to have their path
        // prefixed with '/' — without it EMI logs "Recipe X not present in recipe manager" errors.
        return ResourceLocation.fromNamespaceAndPath("multilib", "/" + data.definition().getId().getPath());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        // data.inputs() already lists a representative stack per component block (see
        // MultiblockRecipeDisplay.of), so EMI's "Uses" lookup finds this recipe from any of them.
        return data.inputs().stream().map(s -> (EmiIngredient) EmiStack.of(s)).collect(Collectors.toList());
    }

    @Override
    public List<EmiStack> getOutputs() {
        // data.outputs() is just the core/activation block (see MultiblockRecipeDisplay.of), so
        // EMI's "Recipes" lookup on it shows only this specific multiblock, not the whole category.
        return data.outputs().stream().map(EmiStack::of).collect(Collectors.toList());
    }

    @Override
    public int getDisplayWidth() {
        return 176;
    }

    @Override
    public int getDisplayHeight() {
        // Shorter than JEI's 296: EMI's recipe-view screen spends extra vertical space above the
        // per-recipe area on its own chrome (the category tab/arrows bar, plus a "Page X of Y" bar),
        // which JEI's recipes GUI doesn't reserve the same way. At 296 the panel's own
        // required-blocks list rendered past where EMI's recipe box actually ends, spilling into the
        // screen area below it (the search bar/sidebar) instead of sitting inside the box.
        //
        // 240 still isn't safe: verified against EMI 1.1.24 sources (RecipeScreen#init,
        // EmiConfig.maximumRecipeScreenHeight = 256 by default; RecipeTab#constructWidgets places
        // each recipe's widget group at y + 37 + off, i.e. 37px of fixed header chrome above the
        // recipe's own content on every page; RecipeDisplay#addButtons anchors EMI's own right-side
        // buttons — recipe-tree/"set as default" — at y = height + DISPLAY_PADDING/2 - 12 - space/2,
        // which for 2 stacked 12px buttons bottoms out 2px below our declared height). So the real
        // vertical budget per recipe page is 256 - 37 - 2 = 217px; at 240 we already overflow EMI's
        // own page cap by 25px, which is exactly what pushed those buttons below the visible panel.
        // 200 leaves a comfortable margin under that 217px ceiling.
        return 200;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new MultiblockPreviewWidget(data.definition(), 0, 0, getDisplayWidth(), getDisplayHeight()));

        // EMI dev-mode logs "Recipe's slots do not include any outputs / Call SlotWidget.recipeContext(this)
        // on outputs" for any recipe that never registers a SlotWidget for its output — our panel draws
        // everything manually via GuiGraphics (MultiblockPreviewPanel), so EMI never otherwise sees an
        // official output slot. That silently disables output-slot-driven features: recipe-tree
        // resolution, the "set as default recipe" toggle, and favoriting via this recipe (see
        // SlotWidget#recipeContext's javadoc and WidgetGroup#decorateDevMode's exact check, both in
        // EMI 1.1.24 sources — decorateDevMode only scans for *any* SlotWidget in this group whose
        // getRecipe() != null, it doesn't care about size or position). Register a real SlotWidget tied
        // to our actual output (the core/activation block) to satisfy that contract. getBounds() is
        // overridden to shrink it from the default 18x18 down to 1x1 at the top-left corner (0,0): a
        // 1px hit-box is negligible for click/hover interference with the panel's own title area
        // underneath, and drawBack(false) means nothing is actually painted there either — it exists
        // purely to give EMI its required output-slot reference, not to be seen or clicked.
        List<EmiStack> outputs = getOutputs();
        if (!outputs.isEmpty()) {
            widgets.add(new SlotWidget(outputs.get(0), 0, 0) {
                @Override
                public dev.emi.emi.api.widget.Bounds getBounds() {
                    return new dev.emi.emi.api.widget.Bounds(0, 0, 1, 1);
                }
            }.drawBack(false).recipeContext(this));
        }
    }
}

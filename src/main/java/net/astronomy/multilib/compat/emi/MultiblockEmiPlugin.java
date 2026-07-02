package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.astronomy.multilib.client.RecipeViewerLink;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;

/**
 * EMI plugin for MultiLib. Auto-discovered by EMI via {@link EmiEntrypoint}.
 * Do NOT register this class manually in MultiLib.java.
 */
@EmiEntrypoint
@SuppressWarnings("unused")
public class MultiblockEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiInputBridge.init();
        EmiRecipeCategory category = MultiblockEmiRecipe.getOrCreateCategory();
        registry.addCategory(category);
        // Deliberately NOT registered as a "workstation" (dev.emi.emi.api.EmiRegistry#addWorkstation):
        // EmiApi#displayUses merges byWorkstation results in on TOP of the normal input/output-based
        // lookup, so a workstation-bound item always surfaced every recipe in the category regardless
        // of which one it actually belonged to — that's what caused every structure to show up no
        // matter which core/activation block was opened. MultiblockRecipeDisplay.of(...) now gives
        // each recipe a real per-definition output (see MultiblockEmiRecipe#getOutputs), so EMI's
        // standard "Recipes" lookup on an item already filters correctly without a workstation.
        MultiblockRegistry.getAllDefinitions().forEach(def ->
                registry.addRecipe(new MultiblockEmiRecipe(MultiblockRecipeDisplay.of(def))));

        // Lets other MultiLib compat modules (e.g. compat/ftbquests) open "recipes producing this stack" without depending on EMI directly.
        RecipeViewerLink.register(stack -> EmiApi.displayRecipes(EmiStack.of(stack)));
    }
}

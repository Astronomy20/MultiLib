package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.astronomy.multilib.client.RecipeViewerLink;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

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
        // of which one it actually belonged to - that's what caused every structure to show up no
        // matter which core/activation block was opened. MultiblockRecipeDisplay.of(...) now gives
        // each recipe a real per-definition output (see MultiblockEmiRecipe#getOutputs), so EMI's
        // standard "Recipes" lookup on an item already filters correctly without a workstation.
        Map<ResourceLocation, MultiblockEmiRecipe> byDefinitionId = new HashMap<>();
        // One recipe per variant - see MultiblockJeiPlugin#registerRecipes for the same convention.
        // putIfAbsent keeps RecipeViewerLink pointed at the parent (first/primary) variant's page.
        MultiblockRegistry.getAllDefinitions().forEach(def -> def.getAllVariants().forEach(variant -> {
            MultiblockEmiRecipe recipe = new MultiblockEmiRecipe(MultiblockRecipeDisplay.of(variant));
            registry.addRecipe(recipe);
            byDefinitionId.putIfAbsent(def.getId(), recipe);
        }));

        // Lets other MultiLib compat modules (e.g. compat/ftbquests) open this exact multiblock's
        // recipe page without depending on EMI directly. Opens the recipe instance directly
        // (EmiApi#displayRecipe) rather than by ItemStack - a stack lookup would also surface
        // unrelated recipes that happen to output the same core/activation block. See
        // RecipeViewerLink's javadoc.
        RecipeViewerLink.register(def -> {
            MultiblockEmiRecipe recipe = byDefinitionId.get(def.getId());
            if (recipe != null) EmiApi.displayRecipe(recipe);
        });
    }
}

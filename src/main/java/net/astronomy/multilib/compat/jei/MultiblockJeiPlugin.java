package net.astronomy.multilib.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.astronomy.multilib.client.RecipeViewerLink;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JEI plugin for MultiLib. Auto-discovered by JEI via {@link JeiPlugin}.
 * Do NOT register this class manually — JEI scans for the annotation at runtime.
 */
@JeiPlugin
@SuppressWarnings("unused")
public class MultiblockJeiPlugin implements IModPlugin {

    private MultiblockRecipeCategory category;
    private final Map<ResourceLocation, MultiblockRecipeDisplay> byDefinitionId = new HashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("multilib", "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        JeiScreenResetHandler.init();
        category = new MultiblockRecipeCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(category);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<MultiblockRecipeDisplay> recipes = MultiblockRegistry.getAllDefinitions().stream()
                .map(MultiblockRecipeDisplay::of)
                .toList();
        registration.addRecipes(MultiblockRecipeCategory.RECIPE_TYPE, recipes);
        byDefinitionId.clear();
        recipes.forEach(r -> byDefinitionId.put(r.definition().getId(), r));
    }

    /**
     * Lets other MultiLib compat modules (e.g. compat/ftbquests) open this exact multiblock's recipe
     * page without depending on JEI directly. Opens the display directly via
     * {@link mezz.jei.api.runtime.IRecipesGui#showRecipes} rather than focusing by ItemStack — a
     * stack focus would also surface unrelated recipes that happen to output the same core/activation
     * block (e.g. a structure whose core is an emerald block would land on the emerald block's own
     * crafting recipe instead of the multiblock tab). See RecipeViewerLink's javadoc.
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeViewerLink.register(def -> {
            MultiblockRecipeDisplay display = byDefinitionId.get(def.getId());
            if (display != null && category != null) {
                runtime.getRecipesGui().showRecipes(category, List.of(display), List.of());
            }
        });
    }
}

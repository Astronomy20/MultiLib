package net.astronomy.multilib.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * JEI plugin for MultiLib. Auto-discovered by JEI via {@link JeiPlugin}.
 * Do NOT register this class manually — JEI scans for the annotation at runtime.
 */
@JeiPlugin
@SuppressWarnings("unused")
public class MultiblockJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("multilib", "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new MultiblockRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<MultiblockRecipeDisplay> recipes = MultiblockRegistry.getAllDefinitions().stream()
                .map(MultiblockRecipeDisplay::of)
                .toList();
        registration.addRecipes(MultiblockRecipeCategory.RECIPE_TYPE, recipes);
    }
}

package net.astronomy.multilib.compat.rei;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.astronomy.multilib.client.RecipeViewerLink;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;

/**
 * REI client plugin for MultiLib.
 *
 * Discovery on NeoForge: REI scans for the {@link REIPluginClient} annotation via NeoForge's mod
 * annotation index (see {@code me.shedaniel.rei.forge.PluginDetectorImpl}), not Java ServiceLoader -
 * so this class must carry {@code @REIPluginClient} rather than a {@code META-INF/services} entry.
 * Do NOT register this class manually in MultiLib.java.
 */
@REIPluginClient
@SuppressWarnings("unused")
public class MultiblockReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        ReiScreenResetHandler.init();
        registry.add(new MultiblockCategory());
        // Lets other MultiLib compat modules (e.g. compat/ftbquests) open this exact multiblock's
        // recipe page without depending on REI directly. filterCategory restricts the search to our
        // own category - without it, addRecipesFor(stack) alone would also surface unrelated recipes
        // that happen to output the same core/activation block (e.g. a structure whose core is an
        // emerald block would land on the emerald block's own crafting recipe instead of the
        // multiblock tab). See RecipeViewerLink's javadoc.
        RecipeViewerLink.register(def -> {
            var catalyst = MultiblockRecipeDisplay.catalystStack(def);
            if (!catalyst.isEmpty()) {
                ViewSearchBuilder.builder()
                        .filterCategory(MultiblockCategory.ID)
                        .addRecipesFor(EntryStacks.of(catalyst))
                        .open();
            }
        });
        // Deliberately NOT registered as a "workstation": REI workstations open the WHOLE category
        // regardless of which item you clicked, which is why every structure used to show up no
        // matter which core/activation block was opened. MultiblockRecipeDisplay.of(...) now gives
        // each display a real per-definition output (its own core/activation block, see
        // registerDisplays below) - REI's standard "Recipes" lookup on an item already filters by
        // output, so clicking a specific block correctly surfaces only its own structure.

        // Suppress REI's built-in "+" quick-craft button (bottom-right of the recipe box). It exists
        // to trigger a registered TransferHandler (e.g. "move items into a crafting grid"), which has
        // no meaning for a multiblock structure - there's nothing to auto-craft/transfer. With no
        // TransferHandler registered for this category, REI's own AutoCraftingEvaluator finds nothing
        // applicable (AutoCraftingEvaluator#evaluateAutoCrafting never sets hasApplicable) and renders
        // the button as a red "!" instead of "+", which reads as a stray error icon.
        //
        // removePlusButton()/setPlusButtonArea(bounds -> null) does NOT actually remove it, despite the
        // name - confirmed by decompiling CategoryRegistryImpl.CategoryConfigurationImpl in
        // RoughlyEnoughItems-neoforge-16.0.799.jar: getPlusButtonArea() unconditionally returns
        // Optional.of(...) (so DefaultDisplayViewingScreen's `plusButtonArea.isPresent()` check never
        // skips adding the widget) and wraps the provider in
        // Objects.requireNonNullElseGet(providerResult, () -> ButtonArea.defaultArea().get(bounds)),
        // silently substituting the default bottom-right area whenever the provider returns null. This
        // matches the API's own javadoc: "@deprecated No longer supported, the plus button is not
        // available for removal."
        //
        // A zero-size Rectangle at the display's own origin was tried next, but the button widget
        // apparently renders/hit-tests at a fixed intrinsic size regardless of the declared width/height
        // (it just showed up anchored at the top-left corner instead of hidden). Since REI unconditionally
        // adds this widget no matter what the area provider returns, the only reliable way left to hide
        // it is to place it far outside the visible screen entirely rather than relying on its size.
        registry.get(MultiblockCategory.ID).setPlusButtonArea(bounds -> new Rectangle(-100000, -100000, 10, 10));
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        // One display per variant - see MultiblockJeiPlugin#registerRecipes for the same convention.
        MultiblockRegistry.getAllDefinitions().forEach(def ->
                def.getAllVariants().forEach(variant ->
                        registry.add(new MultiblockDisplay(MultiblockRecipeDisplay.of(variant))))
        );
    }
}

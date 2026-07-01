package net.astronomy.multilib.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.forge.REIPluginClient;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;

/**
 * REI client plugin for MultiLib.
 *
 * Discovery on NeoForge: REI scans for the {@link REIPluginClient} annotation via NeoForge's mod
 * annotation index (see {@code me.shedaniel.rei.forge.PluginDetectorImpl}), not Java ServiceLoader —
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
        // Deliberately NOT registered as a "workstation": REI workstations open the WHOLE category
        // regardless of which item you clicked, which is why every structure used to show up no
        // matter which core/activation block was opened. MultiblockRecipeDisplay.of(...) now gives
        // each display a real per-definition output (its own core/activation block, see
        // registerDisplays below) — REI's standard "Recipes" lookup on an item already filters by
        // output, so clicking a specific block correctly surfaces only its own structure.

        // Suppress REI's built-in "+" quick-craft button (bottom-right of the recipe box). It exists
        // to trigger a registered TransferHandler (e.g. "move items into a crafting grid"), which has
        // no meaning for a multiblock structure — there's nothing to auto-craft/transfer. With no
        // TransferHandler registered for this category, REI's own AutoCraftingEvaluator finds nothing
        // applicable and renders the button as a red "!" instead of "+", which reads as a stray error
        // icon. removePlusButton() is deprecated in favor of setPlusButtonArea(...), but its javadoc
        // ("no longer supported... not available for removal") is about a batch API being retired, not
        // about this shim not working: it's implemented as exactly setPlusButtonArea(bounds -> null),
        // REI's own sanctioned way to opt a category out of the button (see ButtonArea.get, whose null
        // return this widget checks before rendering anything for that area).
        registry.get(MultiblockCategory.ID).removePlusButton();
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        MultiblockRegistry.getAllDefinitions().forEach(def ->
                registry.add(new MultiblockDisplay(MultiblockRecipeDisplay.of(def)))
        );
    }
}

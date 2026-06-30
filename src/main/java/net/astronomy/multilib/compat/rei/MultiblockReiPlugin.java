package net.astronomy.multilib.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.astronomy.multilib.core.registry.MultiblockRegistry;

/**
 * REI client plugin for MultiLib.
 *
 * Discovery on NeoForge: REI uses Java ServiceLoader. This class is listed in
 * {@code META-INF/services/me.shedaniel.rei.api.client.plugins.REIClientPlugin}.
 * Do NOT register this class manually in MultiLib.java.
 */
@SuppressWarnings("unused")
public class MultiblockReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new MultiblockCategory());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        MultiblockRegistry.getAllDefinitions().forEach(def ->
                registry.add(new MultiblockDisplay(MultiblockRecipeDisplay.of(def)))
        );
    }
}

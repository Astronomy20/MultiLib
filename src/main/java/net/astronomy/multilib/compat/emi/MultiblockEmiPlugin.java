package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
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
        registry.addCategory(MultiblockEmiRecipe.getOrCreateCategory()); // TODO: verify API version compatibility
        MultiblockRegistry.getAllDefinitions().forEach(def ->
                registry.addRecipe(new MultiblockEmiRecipe(MultiblockRecipeDisplay.of(def)))
        );
    }
}

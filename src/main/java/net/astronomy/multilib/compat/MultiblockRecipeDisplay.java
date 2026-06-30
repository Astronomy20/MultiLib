package net.astronomy.multilib.compat;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shared display record used by all item-recipe-browser integrations (JEI, REI, EMI).
 * inputs/outputs are intentionally empty for pure structure definitions; mod devs can
 * subclass MultiblockDefinition or register separate recipes to populate them.
 */
public record MultiblockRecipeDisplay(
        MultiblockDefinition definition,
        List<ItemStack> inputs,
        List<ItemStack> outputs
) {
    public static MultiblockRecipeDisplay of(MultiblockDefinition def) {
        return new MultiblockRecipeDisplay(def, List.of(), List.of());
    }
}

package net.astronomy.multilib.compat;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared display record used by all item-recipe-browser integrations (JEI, REI, EMI).
 * <p>
 * {@code inputs} lists a representative stack for every distinct block the pattern requires (so
 * "what uses this item" lookups find the structure from any of its component blocks), and
 * {@code outputs} is the core/activation block alone (so "what produces this item" / right-click
 * "Recipes" lookups resolve to exactly this one structure, not the whole category - see
 * {@link #catalystStack}). Both are consumed identically by all three viewers; see
 * {@code compat/jei/MultiblockRecipeCategory}, {@code compat/rei/MultiblockReiPlugin}, and
 * {@code compat/emi/MultiblockEmiPlugin}.
 */
public record MultiblockRecipeDisplay(
        MultiblockDefinition definition,
        List<ItemStack> inputs,
        List<ItemStack> outputs
) {
    public static MultiblockRecipeDisplay of(MultiblockDefinition def) {
        List<ItemStack> inputs = new ArrayList<>();
        for (BlockIngredient ing : def.getBlockMap().values()) {
            ItemStack s = MultiblockPreviewPanel.representativeStack(ing);
            if (!s.isEmpty()) inputs.add(s);
        }
        ItemStack catalyst = catalystStack(def);
        List<ItemStack> outputs = catalyst.isEmpty() ? List.of() : List.of(catalyst);
        return new MultiblockRecipeDisplay(def, inputs, outputs);
    }

    /**
     * A representative {@link ItemStack} for this definition's core/activation block - the item
     * that uniquely identifies this specific structure (used as this display's sole {@link #outputs}
     * entry, and to register a recipe-browser "catalyst"/"workstation" so the category is reachable
     * from REI/EMI/JEI's item-driven UIs at all, not just internally registered).
     *
     * @return the representative stack, or {@link ItemStack#EMPTY} if none of the definition's
     *         candidate blocks for its activation/core symbol have an item form.
     */
    public static ItemStack catalystStack(MultiblockDefinition def) {
        char actSym = def.getActivationSymbol();
        char corSym = def.getCoreSymbol();
        char disSym = actSym != '\0' ? actSym : corSym;
        if (disSym != '\0') {
            BlockIngredient ing = def.getBlockMap().get(disSym);
            if (ing != null) {
                ItemStack stack = MultiblockPreviewPanel.representativeStack(ing);
                if (!stack.isEmpty()) return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}

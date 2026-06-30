package net.astronomy.multilib.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REI Display wrapper for {@link MultiblockRecipeDisplay}.
 * inputs/outputs are empty by default; they become populated when a definition
 * provides item stacks.
 */
public class MultiblockDisplay implements Display {

    private final MultiblockRecipeDisplay data;

    public MultiblockDisplay(MultiblockRecipeDisplay data) {
        this.data = data;
    }

    public MultiblockRecipeDisplay getData() {
        return data;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        if (data.inputs().isEmpty()) return List.of();
        return data.inputs().stream()
                .map(EntryIngredients::of)
                .collect(Collectors.toList());
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        if (data.outputs().isEmpty()) return List.of();
        return data.outputs().stream()
                .map(EntryIngredients::of)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return MultiblockCategory.ID;
    }
}

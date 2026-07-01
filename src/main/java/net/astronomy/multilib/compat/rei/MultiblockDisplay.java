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
        // data.inputs() already lists a representative stack per component block (see
        // MultiblockRecipeDisplay.of), so REI's "Uses" lookup finds this recipe from any of them.
        return data.inputs().stream().map(EntryIngredients::of).collect(Collectors.toList());
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        // data.outputs() is just the core/activation block (see MultiblockRecipeDisplay.of), so
        // REI's "Recipes" lookup on it shows only this specific multiblock, not the whole category.
        return data.outputs().stream().map(EntryIngredients::of).collect(Collectors.toList());
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return MultiblockCategory.ID;
    }
}

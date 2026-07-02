package net.astronomy.multilib.client;

import net.astronomy.multilib.api.definition.MultiblockDefinition;

import java.util.function.Consumer;

/**
 * Client-side indirection so an always-compiled caller (e.g. {@code compat/ftbquests}) can ask "open
 * whichever recipe viewer is installed, focused on this specific multiblock" without depending on
 * JEI/REI/EMI at compile time. Each of {@code compat/jei}, {@code compat/rei}, {@code compat/emi}
 * (already individually gated on their own optional dependency) registers itself here if loaded —
 * this class itself has zero dependencies and is always compiled, so callers never need reflection or
 * a ModList check of their own.
 * <p>
 * Deliberately keyed by {@link MultiblockDefinition} rather than a representative {@code ItemStack}:
 * opening "recipes producing this item" would also surface unrelated vanilla/modded recipes that
 * happen to output the same core/activation block (e.g. clicking a task for a structure whose core is
 * an emerald block would land on the emerald block's own crafting recipe instead of the multiblock
 * tab). Each registered opener instead looks up its own already-built recipe/display for this exact
 * definition and opens that directly.
 */
public final class RecipeViewerLink {
    private static volatile Consumer<MultiblockDefinition> opener = null;

    private RecipeViewerLink() {}

    /** Called by whichever recipe-viewer compat plugin (JEI/REI/EMI) actually loads. Last one wins if more than one is installed. */
    public static void register(Consumer<MultiblockDefinition> newOpener) {
        opener = newOpener;
    }

    public static boolean isAvailable() {
        return opener != null;
    }

    /** No-ops if no recipe viewer registered itself. */
    public static void open(MultiblockDefinition definition) {
        Consumer<MultiblockDefinition> current = opener;
        if (current != null) current.accept(definition);
    }
}

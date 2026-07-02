package net.astronomy.multilib.client;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Client-side indirection so an always-compiled caller (e.g. {@code compat/ftbquests}) can ask "open
 * whichever recipe viewer is installed on this item" without depending on JEI/REI/EMI at compile time.
 * Each of {@code compat/jei}, {@code compat/rei}, {@code compat/emi} (already individually gated on
 * their own optional dependency) registers itself here if loaded — this class itself has zero
 * dependencies and is always compiled, so callers never need reflection or a ModList check of their own.
 */
public final class RecipeViewerLink {
    private static volatile Consumer<ItemStack> opener = null;

    private RecipeViewerLink() {}

    /** Called by whichever recipe-viewer compat plugin (JEI/REI/EMI) actually loads. Last one wins if more than one is installed. */
    public static void register(Consumer<ItemStack> newOpener) {
        opener = newOpener;
    }

    public static boolean isAvailable() {
        return opener != null;
    }

    /** No-ops if no recipe viewer registered itself, or if {@code catalyst} is empty. */
    public static void open(ItemStack catalyst) {
        Consumer<ItemStack> current = opener;
        if (current != null && !catalyst.isEmpty()) current.accept(catalyst);
    }
}

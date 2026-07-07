package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.process.RecipeProcessor;

import java.util.Optional;

/**
 * Opt-in hook a controller block entity implements so {@link ProcessHudProvider} can find its
 * {@link RecipeProcessor} without MultiLib knowing anything about the dev's block entity layout.
 * MultiLib never assumes a controller has a process at all - a BE that doesn't implement this interface
 * is simply skipped by {@link ProcessHudProvider}.
 */
public interface HudProcessSource {

    /** The processor to report progress for, or empty if this controller currently has none to show. */
    Optional<RecipeProcessor<?, ?>> getHudProcessor();
}

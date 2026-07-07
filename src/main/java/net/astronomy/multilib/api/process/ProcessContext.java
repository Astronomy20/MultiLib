package net.astronomy.multilib.api.process;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Everything a {@link ProcessRecipe} needs to inspect or mutate a single machine's state for one call:
 * the level it's running in, the controller block entity that owns the {@link RecipeProcessor}, and an
 * arbitrary dev-supplied data object.
 * <p>
 * Deliberately minimal - MultiLib has no opinion on items, fluids, or energy, so this does not carry
 * inventory/tank/energy-storage handles directly. {@code data} is where the dev plugs those in: a
 * container of item/fluid/energy handlers, a reference to the specific recipe-input slots being checked,
 * or simply {@code null}/{@code Void} if the controller BE itself already exposes everything the recipe
 * needs.
 * <p>
 * A fresh {@code ProcessContext} is expected to be constructed by the dev on every tick (it's a thin,
 * cheap wrapper) rather than cached, since the {@link ServerLevel} and data object it wraps may become
 * stale across ticks (e.g. after a chunk unload/reload).
 *
 * @param level      the server level the controller is placed in
 * @param controller the controller block entity that owns the {@link RecipeProcessor} driving this recipe
 * @param data       arbitrary dev-supplied data (inventory handles, capability references, etc.); may be
 *                   {@code null} if the recipe only needs {@code controller} itself
 * @param <BE>       the dev's controller block entity type
 * @param <D>        the dev's arbitrary data type
 */
public record ProcessContext<BE extends BlockEntity, D>(ServerLevel level, BE controller, D data) {
}

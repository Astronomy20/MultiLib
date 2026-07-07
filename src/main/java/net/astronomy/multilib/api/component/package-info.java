/**
 * Ready-made capability buffers (energy, fluid, items) for multiblock controller block entities, plus
 * a content cache for carrying their state across an unform/reform cycle.
 * <p>
 * Every multiblock controller eventually needs somewhere to put power, fluid, or items - this package
 * exists so that's a one-line field declaration instead of hand-rolled {@code IEnergyStorage}/
 * {@code IFluidHandler}/{@code IItemHandler} boilerplate:
 * <ul>
 *     <li>{@link net.astronomy.multilib.api.component.EnergyBufferComponent} - an {@code IEnergyStorage} buffer.</li>
 *     <li>{@link net.astronomy.multilib.api.component.FluidTankComponent} - an {@code IFluidHandler} tank, with an optional fluid validator.</li>
 *     <li>{@link net.astronomy.multilib.api.component.ItemBufferComponent} - an {@code IItemHandler} inventory, with an optional per-slot validator.</li>
 *     <li>{@link net.astronomy.multilib.api.component.MultiblockComponentHelper} - wires the above into
 *     NeoForge's capability system from the dev's own {@code RegisterCapabilitiesEvent} listener (MultiLib
 *     cannot register these automatically, since it has no knowledge of the dev's BE types or field layout).</li>
 *     <li>{@link net.astronomy.multilib.api.component.ContentCache} - snapshots/restores a set of named
 *     components' NBT across {@code onBroken}/{@code onFormed}, so buffered contents survive a rebuild
 *     instead of resetting to empty.</li>
 * </ul>
 * All three buffer components support an optional {@code onChanged} callback (typically
 * {@code this::setChanged} on the owning BE) so the BE is marked dirty automatically on real content
 * changes, without overriding every accessor by hand. None of this sends chat messages, plays sounds,
 * or otherwise reaches into player-facing UX - it's mechanism only, same as the rest of the API.
 */
package net.astronomy.multilib.api.component;

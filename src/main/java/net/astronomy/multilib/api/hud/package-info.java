/**
 * Neutral, viewer-agnostic hover-info ("HUD") API for formed multiblocks: a mod describes what to show
 * once (as a {@link net.astronomy.multilib.api.hud.MultiblockHudProvider}), and MultiLib bridges it to
 * whichever hover-info viewer mod the player has installed - Jade, The One Probe (see
 * {@code compat/jade}/{@code compat/top}) - without the dev writing a Jade plugin or a TOP provider
 * themselves.
 * <ul>
 *     <li>{@link net.astronomy.multilib.api.hud.HudEntry} - the small, closed set of things a provider
 *     can show: {@link net.astronomy.multilib.api.hud.HudEntry.Text},
 *     {@link net.astronomy.multilib.api.hud.HudEntry.Progress},
 *     {@link net.astronomy.multilib.api.hud.HudEntry.KeyValue}.</li>
 *     <li>{@link net.astronomy.multilib.api.hud.HudContext} - what a provider is handed: the level,
 *     looked-at position, resolved instance/definition, and looking player.</li>
 *     <li>{@link net.astronomy.multilib.api.hud.MultiblockHudProvider} - the dev's callback.</li>
 *     <li>{@link net.astronomy.multilib.api.hud.MultiblockHudRegistry} - registration and gathering,
 *     plus the {@code setHudEnabled}/{@code setUnformedHintsEnabled} opt-out/opt-in switches.</li>
 * </ul>
 * Built-in providers cover the common cases so most mods never need to implement
 * {@code MultiblockHudProvider} from scratch. All of them - including
 * {@link net.astronomy.multilib.api.hud.FormedStatusProvider} - are opt-in: nothing is registered
 * automatically, a definition with no registrations shows nothing on Jade/TOP at all.
 * <p>
 * Straight from a formed instance's own data:
 * {@link net.astronomy.multilib.api.hud.FormedStatusProvider},
 * {@link net.astronomy.multilib.api.hud.TierHudProvider},
 * {@link net.astronomy.multilib.api.hud.StatHudProvider},
 * {@link net.astronomy.multilib.api.hud.ControllerLocationHudProvider},
 * {@link net.astronomy.multilib.api.hud.StructureSizeHudProvider},
 * {@link net.astronomy.multilib.api.hud.PortsSummaryHudProvider},
 * {@link net.astronomy.multilib.api.hud.MissingBlocksProvider}, and
 * {@link net.astronomy.multilib.api.hud.AggregateGroupHudProvider}.
 * <p>
 * Straight from a NeoForge capability at the core: {@link net.astronomy.multilib.api.hud.EnergyHudProvider},
 * {@link net.astronomy.multilib.api.hud.FluidHudProvider}, {@link net.astronomy.multilib.api.hud.ItemHudProvider}.
 * <p>
 * Via an opt-in {@code HudXxxSource} hook the controller block entity implements:
 * {@link net.astronomy.multilib.api.hud.ProcessHudProvider} ({@link net.astronomy.multilib.api.hud.HudProcessSource}),
 * {@link net.astronomy.multilib.api.hud.RecipeHudProvider} (same hook),
 * {@link net.astronomy.multilib.api.hud.RedstoneControlHudProvider} ({@link net.astronomy.multilib.api.hud.HudRedstoneSource}),
 * {@link net.astronomy.multilib.api.hud.OwnershipHudProvider} ({@link net.astronomy.multilib.api.hud.HudOwnershipSource}),
 * {@link net.astronomy.multilib.api.hud.ErrorReasonHudProvider} ({@link net.astronomy.multilib.api.hud.HudErrorSource}),
 * {@link net.astronomy.multilib.api.hud.ComparatorOutputHudProvider} ({@link net.astronomy.multilib.api.hud.HudComparatorSource}).
 * <p>
 * Assembly-level (Fase 12): {@link net.astronomy.multilib.api.hud.AssemblyStatusProvider},
 * {@link net.astronomy.multilib.api.hud.AssemblyAggregateStatHudProvider}.
 *
 * <h2>Registering a custom provider</h2>
 * <pre>{@code
 * MultiblockHudRegistry.register(MY_DEFINITION_ID, (ctx, out) -> {
 *     out.accept(new HudEntry.KeyValue(
 *             Component.literal("Heat"),
 *             Component.literal(getHeat(ctx) + " C")));
 * });
 * }</pre>
 */
package net.astronomy.multilib.api.hud;

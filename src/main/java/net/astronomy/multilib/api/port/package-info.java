/**
 * Port/hatch blocks: dedicated structure positions that expose a formed multiblock's capabilities
 * (item, fluid, energy - anything registered as a NeoForge {@code BlockCapability}) to the outside
 * world, while the actual inventory/tank/buffer lives on the controller's own block entity. This is
 * the same "hatch" pattern used by GregTech, Mekanism, Immersive Engineering and Bigger Reactors:
 * pipes and cables connect to the port block, but its contents are really the controller's.
 * <p>
 * A port that isn't currently part of a formed structure exposes nothing - queries against it simply
 * return empty/{@code null}, the same as if no capability were registered there at all.
 * <p>
 * Quickstart:
 * <ol>
 *     <li>Extend {@link net.astronomy.multilib.api.port.AbstractPortBlockEntity} for the port's block entity.</li>
 *     <li>Extend {@link net.astronomy.multilib.api.port.AbstractPortBlock} for its block, implementing {@code newBlockEntity}.</li>
 *     <li>Place the port's symbol in your pattern like any other part (add a {@code MultiblockAbility} too, only if your controller logic needs to look it up by role).</li>
 *     <li>In a {@code RegisterCapabilitiesEvent} listener, call {@link net.astronomy.multilib.api.port.PortCapabilityHelper#registerProxy} once per capability the port should forward.</li>
 *     <li>Done - any capability query against the port block now transparently reaches its controller.</li>
 * </ol>
 */
package net.astronomy.multilib.api.port;

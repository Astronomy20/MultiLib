package net.astronomy.multilib.api.event;

import net.neoforged.bus.api.Event;

/**
 * Fired once, on the server, right after JSON-datapack multiblock definitions finish (re)loading -
 * i.e. after {@code MultiblockJsonLoader} finishes applying a resource reload (including the very
 * first one, on server start). At this point {@code MultiblockRegistry} holds the full, current set
 * of definitions for this reload cycle: Java-registered ones (registered earlier, at mod setup) plus
 * whatever JSON datapack definitions this reload just (re)loaded.
 * <p>
 * Intended for integrations that need to act on the complete registry state per reload rather than
 * per-definition as they're parsed - e.g. patching an existing definition, which requires it to
 * already be registered. Listen on {@code NeoForge.EVENT_BUS}.
 */
public class MultiblockDefinitionsReloadedEvent extends Event {
}

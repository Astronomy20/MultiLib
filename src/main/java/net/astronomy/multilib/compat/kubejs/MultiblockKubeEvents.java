package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * KubeJS event group exposing MultiLib's create/modify hooks to scripts. Registered with KubeJS via
 * {@link MultiLibKubeJSPlugin}; from JS this becomes
 * {@code MultiblockEvents.create(event => { event.multiblock(id)... })} and
 * {@code MultiblockEvents.modify(event => { event.modify(id, builder => {...}) })} - KubeJS binds
 * both automatically from the names passed below, no manual JS binding needed.
 * <p>
 * Both live in {@code server_scripts}, not {@code startup_scripts}, deliberately: like JSON datapack
 * multiblocks, they re-fire on every {@code MultiblockDefinitionsReloadedEvent} (server start, and
 * every {@code /reload}), so iterating on a definition doesn't require restarting the game - the
 * same dev loop JSON already has. See {@code KubeJSMultiblockSetup}, which re-declares (not
 * strict-registers) every CREATE builder each time for exactly this reason: the same script runs
 * again on every reload, and a second {@code MultiblockRegistry.register()} on the same id would
 * throw.
 * <p>
 * {@code MultiblockEvents.wrench(event => {...})} fires on every wrench interaction (see
 * {@code WrenchInteractionEvent}) with a plain status string - the library itself never shows the
 * player anything on its own; a script wanting feedback listens here.
 */
public interface MultiblockKubeEvents {
    EventGroup GROUP = EventGroup.of("MultiblockEvents");

    EventHandler CREATE = GROUP.server("create", () -> MultiblockCreateKubeEvent.class);
    EventHandler MODIFY = GROUP.server("modify", () -> MultiblockModifyKubeEvent.class);
    EventHandler WRENCH = GROUP.server("wrench", () -> MultiblockWrenchKubeEvent.class);
}

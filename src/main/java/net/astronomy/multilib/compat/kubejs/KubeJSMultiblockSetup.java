package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.script.ConsoleJS;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.MultiblockDefinitionsReloadedEvent;
import net.astronomy.multilib.api.event.WrenchInteractionEvent;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entry point invoked via reflection from {@code MultiLib#commonSetup} once KubeJS is confirmed
 * loaded. Registers for {@link MultiblockDefinitionsReloadedEvent}, which both CREATE and MODIFY run
 * from - including the very first firing on initial server/datapack load, so no separate "run once
 * at startup" path is needed here - and for {@link WrenchInteractionEvent}, bridged into
 * {@code MultiblockEvents.wrench(...)} for scripts.
 */
public final class KubeJSMultiblockSetup {
    private KubeJSMultiblockSetup() {}

    public static void init() {
        NeoForge.EVENT_BUS.register(KubeJSMultiblockSetup.class);
    }

    @SubscribeEvent
    public static void onDefinitionsReloaded(MultiblockDefinitionsReloadedEvent event) {
        MultiblockCreateKubeEvent createEvent = new MultiblockCreateKubeEvent();
        MultiblockKubeEvents.CREATE.post(createEvent);

        // Tracks ids seen in *this* batch only - replace() is designed so the same script can safely
        // re-declare its own multiblock on every reload (removes then re-registers under the same id),
        // but that exact mechanism also means two *different* scripts that happen to declare the same
        // id (e.g. a copy-pasted example script where the id was never renamed) silently clobber each
        // other with no warning at all: the second one processed just replaces the first, and nothing
        // ever reports that the first one's definition was discarded.
        java.util.Set<net.minecraft.resources.ResourceLocation> idsSeenThisBatch = new java.util.HashSet<>();

        for (MultiblockBuilder builder : createEvent.getBuilders()) {
            if (!idsSeenThisBatch.add(builder.getId())) {
                ConsoleJS.SERVER.warn("[MultiLib] Multiblock id '" + builder.getId() + "' is declared more than "
                        + "once in this reload's KubeJS scripts - the earlier declaration was silently replaced. "
                        + "Give each MultiblockEvents.create(...) call a unique id.");
            }
            try {
                // buildWithoutRegistering() + replace() rather than build(): this handler re-runs on
                // every reload, so the same script re-declares the same id each time. replace() is a
                // no-op remove followed by a normal register when the id isn't there yet (first run),
                // and swaps the old definition out otherwise (every run after) - build()'s own
                // MultiblockRegistry.register() would throw "already registered" starting on the
                // second reload.
                MultiblockDefinition definition = builder.buildWithoutRegistering();
                if (definition == null) {
                    // Validation failure (e.g. a duplicate core symbol) is already logged server-side
                    // by buildWithoutRegistering() itself - this is specifically so the same reason also
                    // shows up in the KubeJS console/script error overlay, where a scripter is actually
                    // looking, instead of just pointing them at the server log.
                    ConsoleJS.SERVER.error("[MultiLib] Multiblock '" + builder.getId() + "' not loaded: "
                            + builder.getLastValidationError());
                    continue;
                }
                MultiblockRegistry.replace(definition.getId(), definition, MultiblockRegistry.Source.KUBEJS);
            } catch (Exception e) {
                MultiLib.LOGGER.error("[MultiLib] KubeJS multiblock creation failed", e);
            }
        }

        MultiblockKubeEvents.MODIFY.post(new MultiblockModifyKubeEvent());
    }

    @SubscribeEvent
    public static void onWrenchInteraction(WrenchInteractionEvent event) {
        MultiblockKubeEvents.WRENCH.post(new MultiblockWrenchKubeEvent(event));
    }
}

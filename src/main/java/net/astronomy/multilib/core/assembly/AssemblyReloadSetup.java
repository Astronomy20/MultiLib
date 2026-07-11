package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.MultiLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Auto-registration for the assembly module, mirroring the "solo file nuovi, auto-registrazione via
 * {@code @EventBusSubscriber}" principle of Fase 11: no edits to {@code MultiLib.java}. The game-bus
 * listener adds the datapack loader. {@code api/hud/AssemblyStatusProvider} is NOT wired up here - like
 * every other HUD provider, it's opt-in: a dev who wants it calls
 * {@code MultiblockHudRegistry.registerGlobal(new AssemblyStatusProvider())} themselves.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class AssemblyReloadSetup {

    private AssemblyReloadSetup() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AssemblyJsonLoader());
    }
}

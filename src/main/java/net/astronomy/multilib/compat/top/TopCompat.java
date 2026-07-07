package net.astronomy.multilib.compat.top;

import net.astronomy.multilib.MultiLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Gate for the {@code compat/top} module. Unlike Jade (which auto-discovers {@code @WailaPlugin}
 * classes only once Jade itself is loaded), The One Probe has no annotation-based discovery - an addon
 * has to proactively send TOP an {@code InterModComms} message, which means some always-loaded MultiLib
 * class has to run that code. This class is that always-loaded class (it's on the mod event bus
 * unconditionally), so it deliberately imports nothing from TOP's API: only
 * {@link TopIntegration#register()} - a separate class - does, and that class is only ever touched (via
 * reflection) after {@link ModList#isLoaded} has confirmed TOP is actually present. Mirrors how
 * {@code MultiLib.java} gates {@code compat/ftbquests}/{@code compat/kubejs} the same way.
 * <p>
 * No explicit {@code bus} is set - NeoForge 21.1 routes each annotated method to the mod or game bus
 * automatically based on whether its event type implements {@code IModBusEvent} (which
 * {@link InterModEnqueueEvent} does), and the explicit {@code bus} element is deprecated for removal.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class TopCompat {

    private TopCompat() {}

    @SubscribeEvent
    public static void onInterModEnqueue(InterModEnqueueEvent event) {
        if (!ModList.get().isLoaded("theoneprobe")) return;
        try {
            Class.forName("net.astronomy.multilib.compat.top.TopIntegration")
                    .getMethod("register")
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            MultiLib.LOGGER.error("[MultiLib] Failed to initialize The One Probe compat", e);
        }
    }
}

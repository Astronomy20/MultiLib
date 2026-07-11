package net.astronomy.multilib.example.assembly;

import net.astronomy.multilib.MultiLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Wiring for the Fase 12 assembly demo. Registers {@link ExampleAssembly} at common setup. The
 * assembly only references its member definitions by id, so it can be declared independently of the
 * order in which the {@code basic}/{@code directional} example structures register their patterns. The
 * {@code AssemblyStatusProvider} HUD demo lives in {@code example/hud/ExampleHudSetup}, not here.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class AssemblyExampleSetup {

    private AssemblyExampleSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExampleAssembly.register();
            MultiLib.LOGGER.info("[MultiLib] Example assembly loaded (test build)");
        });
    }
}

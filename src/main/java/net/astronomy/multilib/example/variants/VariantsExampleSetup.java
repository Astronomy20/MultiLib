package net.astronomy.multilib.example.variants;

import net.astronomy.multilib.MultiLib;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Wiring for the pattern-variants demo (F12 variants): one id, two alternative shapes sharing every
 * behavioral field. The demo is built entirely on vanilla blocks (see {@link ExampleVariantsPattern}),
 * so this setup only needs to register the definition at common setup.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class VariantsExampleSetup {

    private VariantsExampleSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExampleVariantsPattern.registerAll();
            MultiLib.LOGGER.info("[MultiLib] Variants example structure loaded (test build)");
        });
    }
}

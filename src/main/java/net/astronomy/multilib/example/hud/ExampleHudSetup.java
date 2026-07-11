package net.astronomy.multilib.example.hud;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.hud.AssemblyStatusProvider;
import net.astronomy.multilib.api.hud.EnergyHudProvider;
import net.astronomy.multilib.api.hud.FormedStatusProvider;
import net.astronomy.multilib.api.hud.MultiblockHudRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Demonstrates {@code api/hud}'s premade providers on Jade/The One Probe. The HUD bridge is a
 * cross-cutting concern, not tied to any one example structure, so every HUD demo registration lives
 * here rather than scattered across {@code example/basic}, {@code example/assembly}, etc. - the same
 * separation the rest of {@code example/} already follows per topic.
 * <p>
 * Every provider registered below is opt-in: {@link MultiblockHudRegistry} starts empty, and a
 * definition with nothing registered for it shows nothing on Jade/TOP, not even its name.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class ExampleHudSetup {

    private ExampleHudSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // The simple premade: definition display name + live state (Idle/Running/Error/...) on
            // every formed multiblock in the test build, including the assembly's own members.
            MultiblockHudRegistry.registerGlobal(new FormedStatusProvider());

            // Assembly membership (role + which assembly), for any member that belongs to one.
            MultiblockHudRegistry.registerGlobal(new AssemblyStatusProvider());

            // Per-definition premade: shows ExampleControllerBE's energy buffer (wired up as a
            // capability in BasicExampleSetup) as an "Energy: X / Y FE" line, only on multilib:example.
            MultiblockHudRegistry.register(id("example"), new EnergyHudProvider());

            MultiLib.LOGGER.info("[MultiLib] Example HUD providers registered (test build)");
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}

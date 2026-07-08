package net.astronomy.multilib.core.preference;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockDevRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the preference wrench - but only when {@code CommonConfig.DEV_MODE} is enabled (checked at
 * {@link RegisterEvent} time), mirroring {@link MultiblockDevRegistry} exactly: this is a dev-only
 * authoring aid, not something a production build of a host mod should ship any trace of. Reuses
 * {@link MultiblockDevRegistry#isDevModeEnabled()} rather than reading {@code CommonConfig.DEV_MODE}
 * directly - see that method's own javadoc for the documented {@code RegisterEvent}-timing workaround
 * this depends on.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class MultiblockPreferenceToolRegistry {

    public static MultiblockPreferenceWrenchItem WRENCH_ITEM;

    private MultiblockPreferenceToolRegistry() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!MultiblockDevRegistry.isDevModeEnabled()) return;

        event.register(Registries.ITEM, helper -> {
            WRENCH_ITEM = new MultiblockPreferenceWrenchItem(new Item.Properties().stacksTo(1));
            helper.register(ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "multiblock_preference_wrench"), WRENCH_ITEM);
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!MultiblockDevRegistry.isDevModeEnabled()) return;
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES && WRENCH_ITEM != null) {
            event.accept(WRENCH_ITEM);
        }
    }
}

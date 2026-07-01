package net.astronomy.multilib.compat.rei;

import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import net.astronomy.multilib.MultiLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Resets every {@link MultiblockCategory.ViewState} (zoom, yaw, pitch, layer, scroll, selection)
 * whenever REI's recipe-view screen is closed, so a freshly (re)opened REI recipe-view window
 * always starts from defaults while still preserving per-definition state across recipe/category
 * switches within one open window — mirrors {@code net.astronomy.multilib.compat.jei.JeiScreenResetHandler}.
 *
 * <p>Unlike JEI (whose recipes screen has no public marker interface, so that handler matches by
 * implementation class name), REI exposes a public API marker interface,
 * {@link DisplayScreen} (implemented by both of REI's recipe-view screen impls —
 * {@code DefaultDisplayViewingScreen} and {@code CompositeDisplayViewingScreen}), so this handler
 * can use a compile-safe {@code instanceof} check instead of an implementation-class-name string.
 */
public class ReiScreenResetHandler {

    private static boolean registered = false;

    public static void init() {
        if (!registered) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ReiScreenResetHandler.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof DisplayScreen) {
            MultiblockCategory.resetAllViewStates();
        }
    }
}

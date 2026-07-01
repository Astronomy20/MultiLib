package net.astronomy.multilib.compat.jei;

import net.astronomy.multilib.MultiLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Resets every {@link net.astronomy.multilib.compat.MultiblockPreviewPanel.ViewState} (zoom, yaw,
 * pitch, layer) whenever the JEI recipes GUI screen is closed, so a freshly (re)opened JEI window
 * always starts from defaults while still preserving per-definition state across recipe/category
 * switches within one open window.
 *
 * <p>Detects JEI's recipes screen by class name instead of importing {@code mezz.jei.gui.recipes.RecipesGui}
 * directly: that class lives in JEI's implementation jar, which this project only depends on at
 * runtime ({@code localRuntime}), not at compile time (only the API jars are {@code compileOnly} —
 * see build.gradle) — an internal import here would break compilation.
 */
public class JeiScreenResetHandler {

    private static final String RECIPES_GUI_CLASS_NAME = "mezz.jei.gui.recipes.RecipesGui";
    private static boolean registered = false;

    public static void init() {
        if (!registered) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(JeiScreenResetHandler.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen().getClass().getName().equals(RECIPES_GUI_CLASS_NAME)) {
            MultiblockRecipeCategory.resetAllViewStates();
        }
    }
}

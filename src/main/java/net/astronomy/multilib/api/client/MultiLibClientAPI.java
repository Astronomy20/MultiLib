package net.astronomy.multilib.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side API surface for consuming mods to configure MultiLib's auto-place modifier key.
 * <p>
 * This is deliberately <b>not</b> a vanilla {@code KeyMapping} and is never registered via
 * {@code RegisterKeyMappingsEvent} — it does not appear as an entry in the Controls menu, and
 * end-players cannot rebind it there. MultiLib is a library mod consumed by other mods, not a
 * gameplay mod played directly, so exposing a Controls-menu option here would be end-user-facing
 * surface that doesn't belong to any single game the player is playing.
 * <p>
 * Instead, the modifier key defaults to Left Ctrl and only changes if the <i>integrating mod's</i>
 * own client-side code explicitly calls {@link #setAutoPlaceModifierKey(int)} — e.g. during client
 * setup — to pick a different key for its own use of MultiLib's auto-place feature.
 */
@OnlyIn(Dist.CLIENT)
public class MultiLibClientAPI {

    private static int autoPlaceModifierKey = InputConstants.KEY_LCONTROL;

    private MultiLibClientAPI() {
    }

    /**
     * Sets the raw GLFW/LWJGL key code used as the auto-place modifier key. Intended to be called
     * by consuming mods from their own client-side setup code, not by MultiLib itself.
     *
     * @param glfwKeyCode a GLFW key constant, e.g. {@link InputConstants#KEY_LCONTROL}
     */
    public static void setAutoPlaceModifierKey(int glfwKeyCode) {
        autoPlaceModifierKey = glfwKeyCode;
    }

    /**
     * @return the raw GLFW/LWJGL key code currently configured as the auto-place modifier key.
     */
    public static int getAutoPlaceModifierKey() {
        return autoPlaceModifierKey;
    }

    /**
     * Checks whether the auto-place modifier key is currently held down, using raw input state
     * rather than a registered {@code KeyMapping}. This reads the same underlying static method
     * that {@code KeyMapping#isDown()} itself delegates to, so it correctly reflects real-time
     * held state without requiring a Controls-menu keybind.
     *
     * @return {@code true} if the configured modifier key is currently pressed.
     */
    public static boolean isAutoPlaceModifierDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), autoPlaceModifierKey);
    }
}

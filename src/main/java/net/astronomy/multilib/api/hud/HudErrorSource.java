package net.astronomy.multilib.api.hud;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Opt-in hook a controller block entity implements so {@link ErrorReasonHudProvider} can show why a
 * structure is in an error state - MultiLib itself never decides what "error" means for a given
 * definition (that's the dev's own {@code MultiblockValidator}/state machine), so this is entirely a
 * dev-supplied message.
 */
public interface HudErrorSource {

    /** The current error reason to show, or empty if nothing is currently wrong. */
    Optional<Component> getHudErrorReason();
}

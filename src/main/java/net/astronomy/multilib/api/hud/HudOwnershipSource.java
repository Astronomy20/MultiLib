package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.control.OwnershipComponent;

/**
 * Opt-in hook a controller block entity implements so {@link OwnershipHudProvider} can find its
 * {@link OwnershipComponent} without MultiLib knowing anything about the dev's block entity layout.
 */
public interface HudOwnershipSource {

    /** The component to report the current owner from. */
    OwnershipComponent getHudOwnership();
}

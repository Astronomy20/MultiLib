package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.control.RedstoneControlComponent;

/**
 * Opt-in hook a controller block entity implements so {@link RedstoneControlHudProvider} can find its
 * {@link RedstoneControlComponent} without MultiLib knowing anything about the dev's block entity
 * layout.
 */
public interface HudRedstoneSource {

    /** The component to report the current redstone mode from. */
    RedstoneControlComponent getHudRedstoneControl();
}

package net.astronomy.multilib.api.hud;

/**
 * Opt-in hook a controller block entity implements so {@link ComparatorOutputHudProvider} can show its
 * current redstone comparator level without recomputing it - the dev's block already derives this value
 * (typically via {@code api/control/ComparatorOutputs}) for its own {@code getAnalogOutputSignal}, this
 * just exposes the same number to the HUD.
 */
public interface HudComparatorSource {

    /** The comparator signal this controller currently outputs, expected in {@code [0, 15]}. */
    int getHudComparatorOutput();
}

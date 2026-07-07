package net.astronomy.multilib.api.hud;

import java.util.function.Consumer;

/**
 * What a mod implements to contribute lines to a formed multiblock's hover info. Registered via
 * {@link MultiblockHudRegistry#registerGlobal} (runs for every formed multiblock) or
 * {@link MultiblockHudRegistry#register} (runs only for one specific definition) - see that class for
 * the full registration/gathering contract, including exception isolation and the dev opt-out
 * killswitch.
 */
@FunctionalInterface
public interface MultiblockHudProvider {

    /**
     * Appends zero or more {@link HudEntry} instances to {@code out} for the multiblock described by
     * {@code ctx}. Called on the server thread; must not block. Any exception thrown here is caught and
     * logged by {@link MultiblockHudRegistry#gatherEntries} - it does not stop other providers from
     * running.
     */
    void appendHudEntries(HudContext ctx, Consumer<HudEntry> out);
}

package net.astronomy.multilib.compat.top;

import mcjty.theoneprobe.api.ITheOneProbe;
import net.neoforged.fml.InterModComms;

import java.util.function.Function;

/**
 * Actual The One Probe registration - only ever touched by {@link TopCompat}, and only after it has
 * confirmed TOP is loaded (see that class's javadoc for why this is split out). Sends the standard
 * {@code getTheOneProbe} IMC message; TOP replies by invoking the supplied
 * {@code Function<ITheOneProbe, Void>} with its own {@link ITheOneProbe} instance, which is used here to
 * register {@link MultiblockTopProvider}.
 */
public final class TopIntegration {

    private TopIntegration() {}

    public static void register() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", TopProbeCallback::new);
    }

    /** The IMC payload: TOP constructs nothing itself, it just calls {@link #apply} with its API instance. */
    private static final class TopProbeCallback implements Function<ITheOneProbe, Void> {
        @Override
        public Void apply(ITheOneProbe theOneProbe) {
            theOneProbe.registerProvider(MultiblockTopProvider.INSTANCE);
            return null;
        }
    }
}

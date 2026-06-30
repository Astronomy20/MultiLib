package net.astronomy.multilib.compat.guideme;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import org.slf4j.LoggerFactory;

/**
 * Placeholder GuideME integration.
 *
 * GuideME does not currently expose a stable programmatic API for multiblock registration.
 * Extend this class when an API becomes available. In the meantime, mod devs should register
 * their GuideME pages in their own datapacks using the standard GuideME JSON format, referencing
 * {@link MultiblockDefinition#getId()} as the identifier.
 */
public final class GuideMEHelper {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("multilib-guideme");

    private GuideMEHelper() {}

    /**
     * Logs a debug message indicating the definition is available for GuideME integration.
     * Call this during registration if you want visibility into which definitions could be wired up.
     */
    public static void logInfo(MultiblockDefinition definition) {
        LOGGER.debug("[MultiLib/GuideME] Definition available for GuideME integration: {}",
                definition.getId());
    }
}

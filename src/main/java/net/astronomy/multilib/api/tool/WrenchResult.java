package net.astronomy.multilib.api.tool;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;

/**
 * What happened when a registered wrench (see {@link IMultiblockWrench}/
 * {@code MultiLibAPI#registerWrenchItem}) was used on a block. Carried by
 * {@code WrenchInteractionEvent} - the library itself never acts on this beyond posting that event;
 * whether (and how) to surface it to the player is entirely up to whoever listens.
 */
public sealed interface WrenchResult {
    /** The clicked block isn't the activation/core block of any registered multiblock. */
    record NotAMultiblock() implements WrenchResult {}

    /** Already formed at this position - nothing was attempted. */
    record AlreadyFormed(MultiblockInstance instance) implements WrenchResult {}

    /**
     * The pattern is actually complete, but this definition's {@code FormationMode} doesn't allow a
     * wrench to be what finishes it (e.g. {@code AUTOMATIC}). Only reported once the pattern is
     * confirmed complete - an incomplete structure always reports {@link FormationFailed} instead,
     * regardless of {@code FormationMode}, so the wrench stays useful as a "what's missing"
     * diagnostic even on structures that only ever form automatically.
     */
    record ModeDisallowsWrench(MultiblockDefinition definition) implements WrenchResult {}

    /** Formation was attempted and succeeded. */
    record Formed(MultiblockDefinition definition) implements WrenchResult {}

    /**
     * The pattern doesn't match (most common), or it did but something else - e.g. a custom
     * validator - rejected the attempt anyway. {@code reason} is the pattern matcher's failure
     * summary in the first case, a generic message in the second.
     */
    record FormationFailed(MultiblockDefinition definition, String reason) implements WrenchResult {}
}

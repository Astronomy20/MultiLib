package net.astronomy.multilib.api.callback;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Common shape shared by {@link MultiblockFormedContext} and {@link MultiblockBrokenContext} - lets
 * code that only cares about "the level, the structure, and one position of interest" (formed: the
 * origin; broken: the removed block) take either without needing two copies of itself, one per event.
 * {@code position()} is deliberately the only place the two contexts actually differ in shape;
 * everything else was already identical on both records.
 */
public interface MultiblockEventContext {
    ServerLevel level();
    MultiblockInstance instance();
    MultiblockDefinition definition();

    /** The origin for a formed structure, or the position of the block whose removal broke it. */
    BlockPos position();
}

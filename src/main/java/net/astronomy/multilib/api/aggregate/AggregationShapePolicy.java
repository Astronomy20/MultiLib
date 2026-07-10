package net.astronomy.multilib.api.aggregate;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Governs which connected clusters of {@link AggregatableBlockEntity} blocks are actually allowed to
 * merge into one {@link AggregateGroup}. Purely a shape predicate over the live, already-connected set
 * that {@link AggregationEngine} found by walking neighbors - it never inspects the world itself, so it
 * has no idea (and no need to know) what kind of content the group holds.
 */
@FunctionalInterface
public interface AggregationShapePolicy {

    /** @param members every block position {@link AggregationEngine} reached from the start position by adjacency alone. */
    boolean isValidShape(Set<BlockPos> members);
}

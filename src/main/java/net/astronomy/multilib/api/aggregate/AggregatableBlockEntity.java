package net.astronomy.multilib.api.aggregate;

import net.minecraft.resources.ResourceLocation;

/**
 * Opt-in marker for a {@code BlockEntity} that should dynamically merge with matching neighbors purely
 * by adjacency - no declared shape, no JSON pattern, no {@code MultiblockDefinition}. This is a
 * completely separate, lightweight mechanism from MultiLib's pattern-matched multiblock system: nothing
 * here knows or cares whether the merged group ends up representing a fluid tank, an energy buffer, or
 * anything else - that's entirely up to whatever implements this interface.
 * <p>
 * Pair with {@link AbstractAggregatingBlock} (or call {@link AggregationEngine}'s static methods
 * directly from your own {@code Block} subclass's {@code onPlace}/{@code onRemove}/
 * {@code neighborChanged}) to get the neighbor-triggered rescans for free.
 * <pre>{@code
 * public class MyTankBlockEntity extends BlockEntity implements AggregatableBlockEntity {
 *     private static final ResourceLocation GROUP = ResourceLocation.fromNamespaceAndPath(MODID, "my_tank");
 *     private AggregateGroup group = AggregationEngine.singleton(GROUP, BlockPos.ZERO);
 *
 *     public ResourceLocation getAggregationGroup() { return GROUP; }
 *     public AggregationShapePolicy getShapePolicy() { return AggregationShapePolicies.cuboid(3, 3, 3); }
 *     public AggregateGroup getAggregateGroup() { return group; }
 *     public void onAggregateChanged(AggregateGroup group) { this.group = group; }
 * }
 * }</pre>
 */
public interface AggregatableBlockEntity {

    /** Only blocks sharing the same group id can ever merge into the same {@link AggregateGroup}. */
    ResourceLocation getAggregationGroup();

    /** Governs which connected shapes are allowed to actually form a group; see {@link AggregationShapePolicies}. */
    AggregationShapePolicy getShapePolicy();

    /** Safety cap on how many blocks a single flood-fill will ever collect (perf/anti-grief guard), AND
     * the merge limit {@link AggregationEngine} enforces before rejecting a would-be merge - applies to
     * every policy, including {@link AggregationShapePolicies#freeform()}, which has no shape constraint
     * of its own to otherwise bound how large a group can grow. */
    default int getMaxAggregateSize() {
        return 512;
    }

    /**
     * This block's own last-computed group, kept up to date by {@link AggregationEngine} - never
     * {@code null} (a block with no merge-worthy neighbor is still a group, just a singleton of itself).
     * {@link AggregationEngine} reads this on every neighboring placement to decide whether a new block
     * can join an already-formed group without re-deriving it from scratch, so a rejected merge never
     * disturbs an already-valid structure.
     */
    AggregateGroup getAggregateGroup();

    /**
     * Called by {@link AggregationEngine} after every recompute with this block's up-to-date group -
     * never {@code null}: a block with no valid neighbors (or whose connected cluster fails the shape
     * policy) still gets a group, just a singleton of itself. Typically just caches {@code group} on a
     * field (so {@link #getAggregateGroup()} can return it) for later use (e.g. building an aggregate
     * content view over {@code group.members()}).
     */
    void onAggregateChanged(AggregateGroup group);
}

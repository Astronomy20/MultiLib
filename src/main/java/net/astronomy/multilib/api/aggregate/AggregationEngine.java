package net.astronomy.multilib.api.aggregate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Content-agnostic neighbor flood-fill for {@link AggregatableBlockEntity}. Knows nothing about fluids,
 * energy, items, or anything else a group might represent - it only ever answers "which blocks does this
 * position currently belong with, and is that shape allowed". No JSON, no declared pattern, no persisted
 * membership: every call walks live world state (and each neighbor's own cached {@link AggregateGroup}),
 * so it's always correct even after {@code /reload}, chunk unload/reload, or another mod editing the
 * world directly.
 * <p>
 * Call {@link #onPlaced}/{@link #onRemoved}/{@link #onNeighborChanged} from a {@code Block}'s matching
 * vanilla hooks - {@link AbstractAggregatingBlock} already does this for you.
 */
public final class AggregationEngine {

    private AggregationEngine() {}

    /**
     * Walks outward from {@code start} over every 6-directionally adjacent {@link AggregatableBlockEntity}
     * sharing {@code self}'s {@linkplain AggregatableBlockEntity#getAggregationGroup() group id}, up to
     * {@link AggregatableBlockEntity#getMaxAggregateSize()} blocks. If the resulting connected set passes
     * {@code self}'s {@link AggregationShapePolicy}, that's the group; otherwise (or if the cap was hit)
     * {@code start} is treated as its own singleton group instead of silently merging into something the
     * policy rejected.
     * <p>
     * This is a one-off read-only query for {@code start} alone - it does NOT push the result to other
     * members, and it does NOT protect any existing structure from being re-derived wholesale. To
     * actually apply a topology change, use {@link #onPlaced}/{@link #onRemoved}/
     * {@link #onNeighborChanged} instead.
     */
    public static AggregateGroup computeGroup(Level level, BlockPos start, AggregatableBlockEntity self) {
        ResourceLocation groupId = self.getAggregationGroup();
        Set<BlockPos> visited = floodFill(level, start, self);
        if (visited.size() > self.getMaxAggregateSize() || !self.getShapePolicy().isValidShape(visited)) {
            return singleton(groupId, start);
        }
        return buildGroup(groupId, visited);
    }

    /** A group of exactly one block - itself its own controller. */
    public static AggregateGroup singleton(ResourceLocation group, BlockPos pos) {
        BlockPos p = pos.immutable();
        return new AggregateGroup(group, Set.of(p), p);
    }

    /**
     * A new block appeared at {@code pos} (placement, or first load of an existing one - call this from
     * {@code onLoad} too, since membership is never persisted). See {@link #tryMerge} for the full
     * "grow if valid, otherwise reject without disturbing anyone else" contract. No-op client-side and if
     * there's nothing aggregatable at {@code pos}.
     */
    public static void onPlaced(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        tryMerge(level, pos);
    }

    /**
     * The block at {@code pos} just disappeared (broken, exploded, etc). Unlike placement, a removal can
     * only ever shrink or split a group that already existed - so for each surviving neighbor whose
     * PRE-removal group actually contained {@code pos} as a member (i.e., a group that's now genuinely
     * broken), that group's survivors are re-partitioned into whatever connected sub-components remain
     * of it and each is validated independently: still a valid shape → becomes its new (possibly smaller)
     * group; no longer valid → every member of that specific sub-component falls back to its own
     * singleton.
     * <p>
     * Deliberately restricted to positions that were already members of the SAME broken group - it never
     * wanders into an unrelated, merely-adjacent block that had previously and independently failed to
     * merge with this group (e.g. one rejected earlier for pushing the group over its size cap). That
     * block stays exactly as excluded as it was; removing something elsewhere must never sweep it back in
     * as a side effect.
     */
    public static void onRemoved(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Set<AggregateGroup> processed = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            if (!(level.getBlockEntity(neighborPos) instanceof AggregatableBlockEntity neighbor)) continue;
            AggregateGroup oldGroup = neighbor.getAggregateGroup();
            if (!oldGroup.members().contains(pos)) continue; // this neighbor's group never included pos - untouched
            if (!processed.add(oldGroup)) continue; // already handled via another neighbor in the same old group
            splitAfterRemoval(level, oldGroup, pos, neighbor);
        }
    }

    /**
     * Something about one of {@code pos}'s neighbors changed (not necessarily an aggregatable block, and
     * not necessarily a placement this engine was told about directly - e.g. another mod swapping a
     * block in directly) - re-attempts the same merge as {@link #onPlaced} for {@code pos}.
     */
    public static void onNeighborChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        tryMerge(level, pos);
    }

    /**
     * Tries to merge {@code pos} into the full connected cluster it's now part of. The candidate is the
     * REAL flood-fill reachable set from {@code pos} - not just its immediate neighbors' cached members -
     * because a bridging block can connect to an existing group through a chain that only touches it
     * indirectly (e.g. completing a 2x2 base into a full 2x2x2 box by placing the last of four blocks on
     * top, where three of the four new corners aren't directly adjacent to each other's own neighbors but
     * all still belong to the one candidate shape). Anything less than the true reachable set would
     * under-count and wrongly reject a shape that's actually complete.
     * <p>
     * If that candidate passes the shape policy (and stays within the size cap), it becomes the new
     * merged group for every member reached, including {@code pos}. If not, {@code pos} alone becomes its
     * own singleton and NOTHING else is touched: every other block reached by the flood-fill - whether
     * part of an already-valid group or already its own rejected singleton - is left exactly as it was.
     * A failed merge attempt must never invalidate a structure that was already valid before {@code pos}
     * showed up, however large or however close to the size limit it already was.
     */
    private static void tryMerge(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AggregatableBlockEntity self)) return;

        // A block that's already part of a validated multi-member group never re-derives itself
        // reactively - vanilla fires neighborChanged on EVERY neighbor of anything placed/changed
        // nearby, including existing members of an already-formed structure that have nothing to do
        // with whatever just happened next to them. Growth/shrink for an already-grouped block only
        // ever comes from someone else's successful tryMerge (which notifies every member of the new
        // candidate directly, see below) or from onRemoved.
        if (!self.getAggregateGroup().isSingleton()) return;

        ResourceLocation groupId = self.getAggregationGroup();
        Set<BlockPos> candidate = floodFill(level, pos, self);

        if (candidate.size() <= self.getMaxAggregateSize() && self.getShapePolicy().isValidShape(candidate)) {
            AggregateGroup merged = buildGroup(groupId, candidate);
            for (BlockPos member : candidate) {
                notify(level, member, merged);
            }
            return;
        }

        // Rejected (bad shape or over cap): pos stands alone, nothing else reached by the flood-fill is
        // touched - it keeps whatever cached group it already had.
        notify(level, pos, singleton(groupId, pos));
    }

    /**
     * Re-partitions {@code oldGroup}'s survivors (every former member except {@code removedPos}, which no
     * longer exists). First tries {@link #tryShrinkAroundRemoval} - a graceful "drop the one whole extreme
     * slice the broken block belonged to" shrink, e.g. breaking a block in a box's topmost Y layer can
     * shrink the box down to exclude just that layer instead of dismantling everything, provided the
     * smaller box left behind is still valid. Whatever isn't kept by that shrink (or everything, if no
     * shrink applied at all) is re-partitioned by raw connectivity AMONG THOSE SURVIVORS ONLY - the
     * flood-fill here never expands into a position outside {@code oldGroup}'s own former membership, so
     * an unrelated adjacent block that was never part of this group can't get pulled in just because this
     * group happened to lose a member nearby. Each resulting sub-component is validated independently.
     */
    private static void splitAfterRemoval(Level level, AggregateGroup oldGroup, BlockPos removedPos,
                                           AggregatableBlockEntity sample) {
        ResourceLocation groupId = oldGroup.groupId();
        AggregationShapePolicy policy = sample.getShapePolicy();
        int maxSize = sample.getMaxAggregateSize();

        Set<BlockPos> survivors = new HashSet<>(oldGroup.members());
        survivors.remove(removedPos);

        Set<BlockPos> leftover = survivors;
        Set<BlockPos> shrunk = tryShrinkAroundRemoval(oldGroup.members(), removedPos, policy, maxSize);
        if (shrunk != null) {
            AggregateGroup newGroup = buildGroup(groupId, shrunk);
            for (BlockPos member : shrunk) {
                notify(level, member, newGroup);
            }
            leftover = new HashSet<>(survivors);
            leftover.removeAll(shrunk);
        }

        Set<BlockPos> remaining = new HashSet<>(leftover);
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            Set<BlockPos> component = floodFillWithin(seed, leftover);
            remaining.removeAll(component);

            if (component.size() <= maxSize && policy.isValidShape(component)) {
                AggregateGroup newGroup = buildGroup(groupId, component);
                for (BlockPos member : component) {
                    notify(level, member, newGroup);
                }
            } else {
                for (BlockPos member : component) {
                    notify(level, member, singleton(groupId, member));
                }
            }
        }
    }

    /**
     * Tries dropping, one axis at a time, the entire slice of {@code oldMembers} that shares
     * {@code removedPos}'s coordinate on that axis - but only for an axis where that coordinate is
     * actually one of the group's two extremes (e.g. the topmost Y layer), since dropping an interior
     * slice would still leave the pieces above and below bridged by whatever else survives in that same
     * slice, which is exactly the ambiguous case left to the connectivity-based fallback instead. Among
     * every axis that qualifies and still yields a shape-policy-valid remainder, returns the LARGEST one
     * (ties broken by axis order X, then Y, then Z) - dropping a thin slice off the group's longest axis
     * naturally preserves the most blocks, which is what makes shrinking a box's height (its usual growth
     * direction) "just work" without hardcoding which axis is supposed to be the height.
     * <p>
     * Returns {@code null} if no axis qualifies, or none of the candidates pass the shape policy.
     */
    private static Set<BlockPos> tryShrinkAroundRemoval(Set<BlockPos> oldMembers, BlockPos removedPos,
                                                         AggregationShapePolicy policy, int maxSize) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : oldMembers) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }

        Set<BlockPos> best = null;
        int rx = removedPos.getX(), ry = removedPos.getY(), rz = removedPos.getZ();
        if (rx == minX || rx == maxX) {
            best = betterShrinkCandidate(best, dropSlice(oldMembers, p -> p.getX() != rx), policy, maxSize);
        }
        if (ry == minY || ry == maxY) {
            best = betterShrinkCandidate(best, dropSlice(oldMembers, p -> p.getY() != ry), policy, maxSize);
        }
        if (rz == minZ || rz == maxZ) {
            best = betterShrinkCandidate(best, dropSlice(oldMembers, p -> p.getZ() != rz), policy, maxSize);
        }
        return best;
    }

    private static Set<BlockPos> dropSlice(Set<BlockPos> members, Predicate<BlockPos> keep) {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos p : members) {
            if (keep.test(p)) result.add(p);
        }
        return result;
    }

    private static Set<BlockPos> betterShrinkCandidate(Set<BlockPos> current, Set<BlockPos> candidate,
                                                         AggregationShapePolicy policy, int maxSize) {
        if (candidate.isEmpty() || candidate.size() > maxSize || !policy.isValidShape(candidate)) return current;
        if (current == null || candidate.size() > current.size()) return candidate;
        return current;
    }

    /** BFS from {@code start} that only ever expands into positions also present in {@code allowed}. */
    private static Set<BlockPos> floodFillWithin(BlockPos start, Set<BlockPos> allowed) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.contains(next) || !allowed.contains(next)) continue;
                visited.add(next);
                queue.add(next);
            }
        }
        return visited;
    }

    /** BFS from {@code start} over live world state, following every same-group {@link AggregatableBlockEntity}. */
    private static Set<BlockPos> floodFill(Level level, BlockPos start, AggregatableBlockEntity self) {
        ResourceLocation group = self.getAggregationGroup();
        int cap = self.getMaxAggregateSize();

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos startImmutable = start.immutable();
        visited.add(startImmutable);
        queue.add(startImmutable);

        while (!queue.isEmpty()) {
            if (visited.size() > cap) break;
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.contains(next)) continue;
                if (!(level.getBlockEntity(next) instanceof AggregatableBlockEntity neighbor)) continue;
                if (!neighbor.getAggregationGroup().equals(group)) continue;
                visited.add(next);
                queue.add(next);
            }
        }
        return visited;
    }

    private static AggregateGroup buildGroup(ResourceLocation groupId, Set<BlockPos> members) {
        BlockPos controller = members.stream()
                .min(Comparator.<BlockPos>comparingInt(p -> p.getY())
                        .thenComparingInt(p -> p.getX())
                        .thenComparingInt(p -> p.getZ()))
                .orElseThrow();
        return new AggregateGroup(groupId, Set.copyOf(members), controller);
    }

    private static void notify(Level level, BlockPos pos, AggregateGroup group) {
        if (level.getBlockEntity(pos) instanceof AggregatableBlockEntity be) {
            be.onAggregateChanged(group);
        }
    }
}

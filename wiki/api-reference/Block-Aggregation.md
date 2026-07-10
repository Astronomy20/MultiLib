[← Back to Home](../index.md)

# Block Aggregation

A **completely separate, lightweight mechanism** from MultiLib's pattern-matched multiblock system. Nothing here knows or cares about `MultiblockDefinition`, JSON patterns, rotation, or the recipe-viewer/ghost-overlay machinery. It answers one question only: *"which currently-placed blocks does this position belong with, and is that shape allowed?"* — think Create's connected fluid tanks/item vaults, not a fixed-layout structure.

Package: `net.astronomy.multilib.api.aggregate`.

## When to reach for this instead of a `MultiblockDefinition`

Use aggregation when the "structure" is really just **N of the same block, freely connected, of variable size** — a tank that grows as you place more tank blocks next to each other, with no fixed layout to declare. Use the regular builder-based system when the structure has a specific shape, a distinct core/controller block, or mixes multiple block types in fixed positions.

## How it works

Nothing is ever persisted. `AggregationEngine` re-derives group membership from live world state on every relevant topology change — placement, removal, a neighbor changing, or a block loading in — by flood-filling outward over same-group neighbors. This means it's always correct after `/reload`, a chunk unload/reload, or another mod editing the world directly; there's nothing to keep in sync across saves.

1. **`AggregatableBlockEntity`** — opt-in marker interface a `BlockEntity` implements. Declares a group id, a shape policy, a size cap, and holds its own last-computed `AggregateGroup`.
2. **`AbstractAggregatingBlock`** — optional convenience `Block` base that wires `AggregationEngine`'s recompute calls into `onPlace`/`onRemove`/`neighborChanged` for you.
3. **`AggregationEngine`** — the static flood-fill/merge/split logic. Content-agnostic: it knows nothing about fluids, energy, items, or anything else a group might represent.
4. **`AggregateGroup`** — an immutable snapshot of one connected, shape-valid cluster: its members and which one is the "controller" (lowest Y, then X, then Z).
5. **`AggregationShapePolicy`** / **`AggregationShapePolicies`** — governs which connected shapes are actually allowed to merge (cuboid, sphere, cylinder, pyramid, or unconstrained).

## `AggregatableBlockEntity`

```java
public interface AggregatableBlockEntity {
    ResourceLocation getAggregationGroup();
    AggregationShapePolicy getShapePolicy();
    default int getMaxAggregateSize() { return 512; }
    AggregateGroup getAggregateGroup();
    void onAggregateChanged(AggregateGroup group);
}
```

- **`getAggregationGroup()`** — only blocks sharing the same group id can ever merge into the same `AggregateGroup`.
- **`getShapePolicy()`** — which connected shapes are valid; see [Shape policies](#shape-policies).
- **`getMaxAggregateSize()`** — safety cap on how large a single flood-fill will ever grow (perf/anti-grief guard), enforced in addition to the shape policy for every policy, including `freeform()`, which has no shape constraint of its own to otherwise bound growth. Defaults to `512`.
- **`getAggregateGroup()`** — this block's own last-computed group; never `null` — a block with no merge-worthy neighbor is still a group, just a singleton of itself.
- **`onAggregateChanged(group)`** — called by `AggregationEngine` after every recompute. Typically just caches `group` on a field so `getAggregateGroup()` can return it, and so an aggregate content view (e.g. a combined fluid handler) can be built over `group.members()`.

Minimal implementation:

```java
public class MyTankBlockEntity extends BlockEntity implements AggregatableBlockEntity {
    private static final ResourceLocation GROUP = ResourceLocation.fromNamespaceAndPath(MODID, "my_tank");
    private AggregateGroup group = AggregationEngine.singleton(GROUP, BlockPos.ZERO);

    public ResourceLocation getAggregationGroup() { return GROUP; }
    public AggregationShapePolicy getShapePolicy() { return AggregationShapePolicies.cuboid(3, 3, 3); }
    public AggregateGroup getAggregateGroup() { return group; }
    public void onAggregateChanged(AggregateGroup group) { this.group = group; }
}
```

Membership is never persisted, so also re-derive it from `onLoad()` (a freshly loaded block otherwise starts out as a lone singleton until something nearby changes):

```java
@Override
public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide()) {
        AggregationEngine.onPlaced(level, worldPosition);
    }
}
```

## `AbstractAggregatingBlock`

Purely plumbing — wires the three vanilla hooks that actually fire on topology changes into `AggregationEngine`, and declares no shape, capacity, or content of any kind:

```java
public abstract class AbstractAggregatingBlock extends Block implements EntityBlock {
    protected void onPlace(...)         { AggregationEngine.onPlaced(level, pos); }
    protected void onRemove(...)        { AggregationEngine.onRemoved(level, pos); }
    protected void neighborChanged(...) { AggregationEngine.onNeighborChanged(level, pos); }
}
```

Not required: a block that can't extend it (already extends something else) calls `AggregationEngine.onPlaced`/`onRemoved`/`onNeighborChanged` directly from its own overrides of those same three hooks instead — that's the entire contract this class fulfills.

## Shape policies

`AggregationShapePolicies` ships ready-made policies. Every one infers the shape's parameters (center, radius, base size, ...) from the connected set's own bounding box, then checks the set exactly matches the ideal discretized shape for those inferred parameters — no extra cells, no missing ones.

| Policy | Shape |
|---|---|
| `cuboid(maxX, maxY, maxZ)` | Solid rectangular prism, no notches/L-shapes/holes, within the given per-axis size |
| `parallelepiped(width, height, depth)` | Alias for `cuboid` |
| `sphere(maxRadius)` | Solid sphere up to `maxRadius` blocks |
| `cylinder(maxRadius, maxHeight)` | Solid upright cylinder (circular footprint extruded along Y) |
| `pyramid(maxBaseSize, maxHeight)` | Stepped square pyramid, each layer up insets by one block per side |
| `freeform()` | Any connected shape at all — Valheim/organic style, no shape constraint beyond adjacency itself |

`freeform()` still respects `getMaxAggregateSize()`, since it has no shape of its own to otherwise bound how large a group can grow.

Implement `AggregationShapePolicy` yourself for anything else — it's a single-method interface (`boolean isValidShape(Set<BlockPos> members)`).

## `AggregateGroup`

```java
public record AggregateGroup(ResourceLocation groupId, Set<BlockPos> members, BlockPos controller) {
    int size();
    boolean isController(BlockPos pos);
    boolean isSingleton();
}
```

A snapshot, not a live view — cheap to recompute any time a member's neighborhood changes, so there's nothing to keep in sync. A block with no valid neighbors of its own is still a group: a `size()` 1 singleton of itself, always its own `controller()`.

## Merge/split behavior

`AggregationEngine` is called from `AbstractAggregatingBlock`'s hooks (or directly, for a block that can't extend it):

- **`onPlaced(level, pos)`** — a new block appeared (placement, or first load — call this from `onLoad()` too). Tries to merge `pos` into the full connected cluster it's now part of, using the *true* flood-fill reachable set, not just immediate neighbors' cached members — a bridging block can complete a shape through a chain that only touches it indirectly. If the candidate passes the shape policy and stays within the size cap, it becomes the new group for every member reached. If not, `pos` alone becomes its own singleton and **nothing else is touched** — a failed merge attempt never invalidates a structure that was already valid.
- **`onRemoved(level, pos)`** — the block at `pos` just disappeared. A removal can only ever shrink or split a group that already existed, never grow one. For each surviving neighbor whose pre-removal group genuinely contained `pos`, that group's survivors are re-partitioned:
  1. First tries a graceful shrink — dropping the one whole extreme slice (e.g. the topmost Y layer) the broken block belonged to, if the smaller remainder is still shape-valid. This is what makes breaking one block off the top of a box shrink the box down cleanly instead of dismantling the whole thing.
  2. Whatever isn't kept by that shrink is re-partitioned by raw connectivity **among the group's own former members only** — the flood-fill never expands into a position outside the old group's membership, so an unrelated adjacent block that was never part of this group can't get pulled in.
  3. Each resulting sub-component is validated independently: still shape-valid → its own (possibly smaller) group; not valid → every member of that sub-component falls back to its own singleton.
- **`onNeighborChanged(level, pos)`** — something about a neighbor changed (not necessarily a placement this engine was told about directly, e.g. another mod swapping a block in). Re-attempts the same merge as `onPlaced`.
- **`computeGroup(level, start, self)`** — a one-off read-only query for `start` alone. Does **not** push the result to other members and does **not** protect an existing structure from being re-derived; use the three methods above to actually apply a topology change.
- **`singleton(group, pos)`** — a group of exactly one block, its own controller.

An already-multi-member, validated group never reactively re-derives itself just because *something* changed next door — vanilla fires `neighborChanged` on every neighbor of anything placed/changed nearby, including existing members with nothing to do with what just happened. Growth/shrink for an already-grouped block only ever comes from another block's successful merge (which notifies every member of the new candidate directly) or from `onRemoved`.

## Building on top: a combined content view

The mechanism only tracks *which positions* form a group — building a combined fluid/energy/item view over `group.members()` is left entirely to the implementer. The example tanks (below) do this by combining each member's own `FluidTankComponent` into one logical `IFluidHandler` whenever the exposed capability is queried, keyed off the current `AggregateGroup`.

## Example: two demo tanks

`net.astronomy.multilib.example` ships two neighbor-aggregating fluid tanks (`example_red_tank`, `example_green_tank`), independent of the `expandable_tank` pattern-matched demo. Neither declares or depends on any `MultiblockDefinition` — both are built entirely on the classes above:

- **`example_red_tank`** — Create-mod-style: `AggregationShapePolicies.cuboid(3, 3, 3)`. An L-shape or offset stack stays as separate independent single-block tanks instead of merging.
- **`example_green_tank`** — `AggregationShapePolicies.freeform()`: any connected shape at all is allowed to merge.

Every block holds its own real, independent tank at a fixed per-block capacity — nothing is resized or transferred when the group grows/shrinks, since each block's own content is exactly what's saved to (and loaded from) that block's own NBT. What scales with the group is only the combined view exposed as the fluid capability: a lone (singleton) block exposes its own standalone tank; a multi-member group combines every member's tank into one logical total, so it doesn't matter which specific block a bucket or pipe touches.

## See also

- [Advanced Features § Block aggregation](../Advanced-Features.md#block-aggregation)
- [Capability Components](Components.md) — `FluidTankComponent`, used by the example tanks for each block's own storage
- [Core Concepts](../Core-Concepts.md) — the pattern-matched multiblock system this mechanism is deliberately separate from

[← Back to Home](../index.md)

# Multiblock Assembly

Packages: `net.astronomy.multilib.api.assembly` (+ `.callback`), `net.astronomy.multilib.core.assembly` (internal), `net.astronomy.multilib.api.blockentity.AbstractAssemblyControllerBE`, `net.astronomy.multilib.command.AssemblyCommands`

An **assembly** links several independent, already-pattern-matched multiblock instances into one logical machine — a reactor core plus N turbines, a fabricator plus its input/output silos — without merging them into a single structure. Each member keeps its own `MultiblockDefinition`, `MultiblockInstance`, contents, and lifecycle; the assembly is a thin overlay that tracks which members currently fill which **role** and reacts when one joins or leaves.

## Why not one big structure?

A single `MultiblockDefinition` has one core and one fixed (or shapeless, still single) shape. An assembly is for when the pieces are separately formed, separately breakable, and the *count* of one piece can vary (1 to N turbines) without redesigning the pattern. Breaking the assembly **never** destroys its members — it only dissolves the logical link; each member remains a fully valid standalone structure.

## Defining an assembly

```java
AssemblyBuilder.create(ResourceLocation.fromNamespaceAndPath("examplemod", "steam_plant"))
        .role("core", ResourceLocation.fromNamespaceAndPath("examplemod", "reactor_core"))               // exactly 1 (default)
        .role("turbine", ResourceLocation.fromNamespaceAndPath("examplemod", "turbine"), 1, 8)            // 1..8
        .role("pipe", ResourceLocation.fromNamespaceAndPath("examplemod", "pipe_segment"), 0, 64)         // optional
        .proximity("core", "turbine", 6)
        .connection("core", "pipe", ConnectionType.ADJACENCY)
        .masterRole("core")
        .breakPolicy(AssemblyBreakPolicy.DEGRADE)
        .aggregateStat("power", StatMerge.SUM)
        .onAssemblyFormed(ctx -> { /* mechanism-only, no chat/sound of its own */ })
        .onMemberJoined(ctx -> { /* ctx.role(), ctx.memberId() */ })
        .onMemberLeft(ctx -> { })
        .onAssemblyBroken(ctx -> { })
        .build();
```

`build()` validates and registers into `AssemblyRegistry`, mirroring `MultiblockBuilder`. `buildWithoutRegister()` exists for loaders that register separately (JSON, KubeJS `modify`). Validation is fail-fast:

- At least one role, and **at least one role with `min >= 1`** — an assembly where every role is optional could never meaningfully form.
- `masterRole`, if set, must reference a declared role.
- Every `connection`'s `fromRole`/`toRole` must reference declared roles.
- `AssemblyFormationPolicy.ATOMIC` is rejected — not implemented in v1 (see [Formation](#formation-bottom-up-only) below).

### `AssemblyRole`

```java
public record AssemblyRole(String name, ResourceLocation definition, int min, int max) {
    public boolean required(); // min >= 1
}
```

A role names a slot and the `MultiblockDefinition` id that can fill it. `.role(name, definitionId)` is shorthand for `min=1, max=1`; `.role(name, definitionId, min, max)` for anything else. `min=0` makes the role optional.

### Connections — the graph

A `ConnectionConstraint` declares that members in two roles must be **connected** for the assembly to form:

```java
public record ConnectionConstraint(String fromRole, String toRole, ConnectionType type, int radius, boolean required) {}

public enum ConnectionType { ADJACENCY, PROXIMITY, PORT_LINK, SHARED_BLOCK }
```

| Type | Connected when |
|---|---|
| `ADJACENCY` | The two members' block sets share at least one face. |
| `PROXIMITY` | Their bounding boxes are within `radius` blocks (Chebyshev distance) — `.proximity(from, to, radius)`. |
| `PORT_LINK` | One member has an [`AbstractPortBlockEntity`](Ports.md) placed against a block of the other. |
| `SHARED_BLOCK` | The two members physically [wall-share](../Advanced-Features.md#wall-sharing) a block. |

If an assembly declares **no** connections at all, members are required to simply be `ADJACENCY`-connected as a fallback, so a connection-free assembly still needs one contiguous cluster rather than accepting any two unrelated members anywhere in the world.

## Formation (bottom-up only)

`AssemblyFormationPolicy.AGGREGATE` (the only implemented policy) is **bottom-up**: members form exactly as they always do — placement, wrench, periodic check, whatever their own `FormationMode` allows — with **zero changes** to that path. `AssemblyMatcher` listens for `MultiblockFormedEvent` on the game bus and, each time a member forms:

1. Tries to **grow** an existing assembly of the right definition that has room in that role and is connected to one of its current members.
2. Otherwise, BFS-walks the connection graph from the newly formed member across other unclaimed formed members, and if the resulting set satisfies every role's `min`/`max`, **promotes** it into a fresh `AssemblyInstance`.
3. If neither succeeds, nothing happens yet — the member just waits, connectable later by a sibling forming nearby.

A member belongs to **at most one** assembly (first-match by priority, then id). `AssemblyFormationPolicy.ATOMIC` (a single trigger forming every member at once) is a documented non-goal for v1.

## Runtime — `AssemblyInstance`

```java
public final class AssemblyInstance {
    public UUID getId();
    public ResourceLocation getDefinitionId();
    public AssemblyState getState();       // FORMED / PARTIAL / RUNNING / UNFORMED, or your own
    public Set<UUID> getMembers(String role);
    public Set<UUID> allMemberIds();
    public int memberCount(String role);
    public Optional<UUID> getFormedBy();
}
```

An assembly **owns no blocks** — `getMembers(role)` returns member `MultiblockInstance` UUIDs, and its footprint is always derived by unioning its members' own positions, never duplicated. `WorldAssemblyTracker` (a per-`ServerLevel` `SavedData`, mirroring `WorldMultiblockTracker`) persists only the assembly↔member UUID links; a member missing at load time (unloaded chunk, lost save) drops the assembly to `PARTIAL` rather than failing to load.

## Breaking — `AssemblyBreakPolicy`

When a member's own structure breaks (the normal single-instance break path, unchanged), `AssemblyBreakHandler` listens for `MultiblockBrokenEvent` and applies the assembly's policy:

| Policy | Behavior |
|---|---|
| `DEGRADE` (default) | If the lost member was optional and every role is still within `[min, max]`, the assembly survives (`onMemberLeft`); otherwise it breaks (`onAssemblyBroken`). |
| `BREAK_ALL` | Any member breaking breaks the whole assembly, regardless of role minimums. |
| `PARTIAL_HOLD` | Never breaks on member loss — drops to `PARTIAL` and recomposes automatically once a replacement member connects. |

Breaking the assembly **never** touches the surviving members' blocks or contents — it only unregisters the logical link.

## Callbacks

```java
public interface AssemblyFormedCallback { void onFormed(AssemblyFormedContext ctx); }
public interface AssemblyBrokenCallback { void onBroken(AssemblyBrokenContext ctx); }
public interface AssemblyMemberJoinedCallback { void onMemberJoined(AssemblyMemberContext ctx); }
public interface AssemblyMemberLeftCallback { void onMemberLeft(AssemblyMemberContext ctx); }
```

All four wrap a shared `AssemblyContext(ServerLevel level, AssemblyInstance instance, AssemblyDefinition definition)`; the member-scoped ones add `role()`/`memberId()`. Every callback runs inside a try/catch (logged, skipped on throw) — one misbehaving listener never breaks another. As with the rest of MultiLib, **nothing here is player-facing by default**: no chat, no sound, unless your own callback adds it.

## Aggregation

The assembly composes its members' own capabilities/stats rather than owning a buffer of its own:

- **`AssemblyCapabilities`** — `collect(level, assembly, capability, side)` / `collectForRole(...)` gathers a NeoForge `BlockCapability` (energy, fluid, item, …) from every member's core position.
- **`AssemblyEnergyView`** — an `IEnergyStorage` built from `AssemblyCapabilities.collect(..., Capabilities.EnergyStorage.BLOCK, null)`: sums `getEnergyStored`/`getMaxEnergyStored`, and fans `receiveEnergy`/`extractEnergy` out across members in order. A view, not a new buffer.
- **`StatMerge`** — `SUM`, `MIN`, `MAX`, `AVG`, applied across members for a given `.aggregateStat(key, merge)` — always explicit, never guessed, matching the rest of `api/tier`.
- **`AssemblyStatAggregator.aggregate(def, perMemberStats)`** — applies every declared `StatMerge` rule to a `Collection<Map<String, Double>>` (one map per member) the caller supplies.

### `AbstractAssemblyControllerBE`

A convenience controller for the `masterRole` member, extending `AbstractMultiblockControllerBE`:

```java
public abstract class AbstractAssemblyControllerBE extends AbstractMultiblockControllerBE {
    public Optional<AssemblyInstance> getAssembly();
    public Optional<AssemblyDefinition> getAssemblyDefinition();
    public boolean isInAssembly();
    public <T> List<T> collectMemberCapabilities(BlockCapability<T, Direction> cap, @Nullable Direction side);
    public <T> List<T> collectRoleCapabilities(String role, BlockCapability<T, Direction> cap, @Nullable Direction side);
    public Optional<AssemblyEnergyView> assemblyEnergy();
}
```

Nothing here ticks the assembly on its own — call these from your own `serverTick()`, same as `RecipeProcessor`.

## JSON / datapack

An assembly is pure data (id references + constraints + policies), so it serializes in full under `data/<namespace>/multilib_assemblies/<name>.json`:

```json
{
  "roles": {
    "core":    { "definition": "examplemod:reactor_core" },
    "turbine": { "definition": "examplemod:turbine", "min": 1, "max": 8 },
    "pipe":    { "definition": "examplemod:pipe_segment", "min": 0, "max": 64 }
  },
  "connections": [
    { "from": "core", "to": "turbine", "type": "port_link" },
    { "from": "core", "to": "pipe", "type": "proximity", "radius": 3 }
  ],
  "master_role": "core",
  "break_policy": "degrade",
  "priority": 0,
  "aggregate_stats": { "power": "sum" }
}
```

`role` without `min`/`max` defaults to `min=1, max=max(1,min)`. `connections[].required` defaults to `true`. Not expressible in JSON: callbacks, custom `AssemblyBreakPolicy`/`AssemblyFormationPolicy` values (only the built-in enum constants). Loaded by `AssemblyJsonLoader`, reloaded cleanly on `/reload` like everything else JSON-driven.

## KubeJS

```js
MultiblockEvents.assembly(event => {
    event.assembly('examplemod:steam_plant')
        .role('core', 'examplemod:reactor_core')
        .role('turbine', 'examplemod:turbine', 1, 8)
        .proximity('core', 'turbine', 6)
        .masterRole('core')
        .breakPolicy('DEGRADE')
        .aggregateStat('power', 'SUM')
})
```

Fires on the same reload cycle as `MultiblockEvents.create`/`modify` (see [KubeJS Integration](../KubeJS-Integration.md)) — a script safely re-declares its assembly on every `/reload`.

## Admin commands

`/multilib assembly` — self-registered, permission level 2, translatable feedback:

| Command | What it does |
|---|---|
| `/multilib assembly list` | Every formed assembly: id, definition, state, member count. |
| `/multilib assembly info <definition>` | A definition's roles (with min/max), break policy, formation policy. |
| `/multilib assembly members <pos>` | The assembly (if any) whose member occupies `pos`, with a per-role member count. |

## HUD

Two opt-in providers (see [HUD Providers](HUD-Providers.md) for the full opt-in policy):

- **`AssemblyStatusProvider`** — the assembly's state plus a per-role member count, shown on any member that belongs to one.
- **`AssemblyAggregateStatHudProvider`** — the actual computed `aggregateStat` numbers (e.g. "power: 480"). Each member's own contribution is its `api/tier` stats summed across whichever symbols declare that key; the assembly's own `StatMerge` is then applied across members.

## Dev-tool export

`core/devtool/AssemblyDevExporter` generates an assembly definition in all three authoring formats (Java/JSON/KubeJS) from an `AssemblyExportSpec` — the assembly-level counterpart of the [in-world dev tool](../Dev-Tools.md)'s per-structure export. The in-world multi-select UI for building the spec interactively is a follow-up; the spec can be assembled by hand today.

## Non-goals (v1)

Recursive assemblies (assembly-of-assemblies), atomic top-down formation, cross-dimension assemblies, and a member belonging to more than one assembly are all explicitly out of scope for the initial release.

## See also

- [MultiblockBuilder](MultiblockBuilder.md), [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md) — the member-level API an assembly composes.
- [MultiblockTier](MultiblockTier.md) — where per-member stats come from for `AssemblyAggregateStatHudProvider`.
- [Ports (Hatches)](Ports.md) — `PORT_LINK` connections.
- [HUD Providers](HUD-Providers.md), [KubeJS Integration](../KubeJS-Integration.md), [Control & Commands](Control-And-Commands.md) — `/multilib assembly` sits alongside `/multilib`.

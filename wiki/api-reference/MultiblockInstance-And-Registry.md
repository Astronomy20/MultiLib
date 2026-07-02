[← Back to Home](../Home.md)

# MultiblockInstance & Registry

Packages: `net.astronomy.multilib.api.instance` (`MultiblockInstance`), `net.astronomy.multilib.core.registry` (`MultiblockRegistry`, `BlockDefinitionRegistry`), `net.astronomy.multilib.core.tracking` (`WorldMultiblockTracker`)

## `MultiblockInstance`

A **formed** structure in the world — created once matching succeeds, and tracked persistently until broken. Unlike the old API's one-shot `PatternAction` reaction, this is a real, NBT-serialized object with identity.

```java
public final class MultiblockInstance {
    public UUID getId();
    public ResourceLocation getDefinitionId();
    public BlockPos getOrigin();
    public TransformData getTransform();
    public boolean contains(BlockPos pos);
    public Set<BlockPos> getPositions();
    public Set<BlockPos> getPositionsFor(char symbol);
    public Optional<BlockPos> getCorePos();
    public Optional<UUID> getFormedBy();
}
```

- `getId()` — stable UUID, assigned at formation, used as the tracker's primary key.
- `getOrigin()` — the world position corresponding to the pattern's top-layer center cell, in the matched orientation.
- `getTransform()` — the `TransformData` (rotation/axis) describing exactly which orientation matched.
- `getPositions()` — every world position this instance occupies.
- `getPositionsFor(char symbol)` — positions matched to a specific pattern symbol (includes free-block positions, if any).
- `getCorePos()` — resolves the core symbol's position(s) via the owning definition, if `hasCore()`.
- `getFormedBy()` — the player who triggered formation, if any (`empty` for anonymous formation, e.g. a dispenser pushing the core into place, or periodic re-formation via `setValidationInterval(...)`). Consulted by `AbstractMultiblockControllerBE` to attribute progression records on state change — see [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md).

Serialized to/from `CompoundTag` (`save()`/`load(tag)`) by `WorldMultiblockTracker`; orphaned instances (definition no longer registered, or corrupted data) are discarded with a logged warning on load rather than crashing world load.

## `MultiblockRegistry`

Process-wide, in-memory registry of every registered `MultiblockDefinition` (Java-defined and JSON-defined alike).

```java
public final class MultiblockRegistry {
    public static void register(MultiblockDefinition definition);
    public static void registerJson(MultiblockDefinition definition);
    public static void clearJsonDefinitions();
    public static Optional<MultiblockDefinition> get(ResourceLocation id);
    public static Collection<MultiblockDefinition> getAll();
    public static List<MultiblockDefinition> getCandidatesFor(Block block);
}
```

- `register(...)` throws `IllegalStateException` if the id is already registered — ids must be unique across the whole game, not just your mod.
- `registerJson(...)` / `clearJsonDefinitions()` — used by the datapack reload listener to swap out JSON-defined definitions on `/reload` without touching Java-defined ones.
- `getCandidatesFor(Block block)` — the core lookup used by `BlockActivationHandler`: returns every definition indexed under `block` (via `BlockIngredient.getCandidateBlocks()`) **plus** every "always-checked" definition (one whose activation/core ingredient can't be enumerated — tags, predicates, `any()`). Results are sorted by `priority` (descending), then **JSON-defined before Java-defined** on ties (the same "data overrides hardcoded defaults" convention vanilla uses for recipes/loot tables/tags).

Prefer `MultiLibAPI.getDefinition(id)` / `getAllDefinitions()` from outside MultiLib itself — this class is the internal implementation.

## `BlockDefinitionRegistry`

Companion registry for block-level metadata (see [BlockDefinition](BlockDefinition.md)):

```java
public final class BlockDefinitionRegistry {
    public static void register(BlockDefinition definition);
    public static Optional<BlockDefinition> get(Block block);
    public static List<Block> getIoPortBlocks();
    public static Optional<ResourceLocation> findDeclaredCoreFor(Block block, ResourceLocation multiblockId);
}
```

## `WorldMultiblockTracker`

A `SavedData` (one per `ServerLevel`, obtained via `WorldMultiblockTracker.get(level)`) tracking every currently-**formed** `MultiblockInstance` in that world/dimension.

```java
public class WorldMultiblockTracker extends SavedData {
    public static WorldMultiblockTracker get(ServerLevel level);
    public void register(MultiblockInstance instance, MultiblockDefinition definition);
    public void unregister(UUID id);
    public Set<MultiblockInstance> getInstancesAt(BlockPos pos);
    public Optional<MultiblockInstance> getById(UUID id);
    public Collection<MultiblockInstance> getAllInstances();
    public void tick(ServerLevel level);
}
```

- Maintains a **position → instance ids** index (`getInstancesAt`), so `BlockBreakHandler` can find affected instances in O(1) rather than scanning every tracked instance on every block break.
- `tick(...)` (driven by `MultiblockTickHandler`'s `LevelTickEvent.Post` subscription) drives every registered `onTick`/`onAmbient` callback — see [Callbacks & Events](Callbacks-And-Events.md).
- Persisted automatically as part of the level's saved data — instances survive server restarts.

You'll mostly interact with this indirectly (via `IMultiblockPart.getInstance(level)` / `getController(level)`, or `AbstractMultiblockControllerBE`'s own `instanceId` field) rather than calling it directly, unless you're writing custom tooling.

## See also

- [Core Concepts § Activation flow](../Core-Concepts.md#activation-flow)
- [Callbacks & Events](Callbacks-And-Events.md)
- [Block Entity Abstractions](BlockEntity-Abstractions.md)
- [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md)

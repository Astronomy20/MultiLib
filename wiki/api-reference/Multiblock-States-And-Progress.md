[← Back to Home](../index.md)

# Multiblock States & Progress Tracking

Packages: `net.astronomy.multilib.api.state` (`MultiblockState`, `MultiblockStateRegistry`, `StandardMultiblockState`), `net.astronomy.multilib.api.event` (`MultiblockStateChangedEvent`), `net.astronomy.multilib.api.progress` (`MultiblockProgressAPI`, `StructureProgress`, `MissingBlock`, `StructureMismatch`, `StructureValidationReport`), `net.astronomy.multilib.core.tracking` (`MultiblockProgressionTracker`), `net.astronomy.multilib.network` (`GhostBlockData`)

Two related but distinct concerns live here:

- **State** - what a *formed* controller's current status is right now (`UNFORMED`/`IDLE`/`RUNNING`/`ERROR`, or a custom state you register), plus a persistent "has this player ever reached state X" record.
- **Progress** - how *complete* an in-progress (not-yet-formed) structure is, computed live from the world for UI like a "you still need N of block X" list.

## `MultiblockState`

```java
public final class MultiblockState {
    public ResourceLocation getId();
    public Optional<String> getNameTranslationKey();
}
```

`MultiblockState` is a plain value object, not an interface you implement - instances can only be created through [`MultiblockStateRegistry`](#multiblockstateregistry). Two `MultiblockState`s are equal iff their `getId()` is equal.

## `MultiblockStateRegistry`

```java
public final class MultiblockStateRegistry {
    public static MultiblockState register(ResourceLocation id);
    public static MultiblockState register(ResourceLocation id, String nameTranslationKey);
    public static Collection<MultiblockState> getAll();
    public static Optional<MultiblockState> get(ResourceLocation id);
}
```

Registering the same `id` twice returns the same instance, so it's safe from a static initializer. `nameTranslationKey` is optional — set it for a readable name where the state is picked from a list (currently the FTB Quests "required state" dropdown).

Registration must happen before the registry freezes (`FMLLoadCompleteEvent`) — do it in your mod constructor or `FMLCommonSetupEvent`, not lazily, or it throws. Declare states as `public static final MultiblockState` fields (like `StandardMultiblockState`) and load the class early enough.

From outside MultiLib, prefer `MultiLibAPI.registerMultiblockState(...)`.

## `StandardMultiblockState`

```java
public final class StandardMultiblockState {
    public static final MultiblockState UNFORMED;
    public static final MultiblockState IDLE;
    public static final MultiblockState RUNNING;
    public static final MultiblockState ERROR;
}
```

Four built-in states under the `multilib` namespace. `AbstractMultiblockControllerBE` uses `UNFORMED`/`IDLE` itself; `RUNNING`/`ERROR` are yours to set via `setState(...)`, nothing sets them automatically. States are just data — MultiLib doesn't validate transitions.

## Controller state lifecycle

`AbstractMultiblockControllerBE.setState(MultiblockState)` (see [Block Entity Abstractions](BlockEntity-Abstractions.md#state)) is the only way a controller's state changes. Every call that actually changes the state (no-op if `newState.equals(currentState)`) does three things, in order:

1. Calls your `onStateChanged(prev, next)` hook.
2. Records progression - see [`MultiblockProgressionTracker`](#multiblockprogressiontracker) below - **if** the controller can resolve a tracked `MultiblockInstance` with a known `formedBy` player. Best-effort: silently skipped if not server-side, not tracked yet, or the structure was formed anonymously (e.g. by a dispenser pushing the core into place) since there's no player to attribute progression to.
3. Posts [`MultiblockStateChangedEvent`](#multiblockstatechangedevent) to `NeoForge.EVENT_BUS`, if step 2 resolved an instance+definition.

`onStructureFormed(...)` calls `setState(StandardMultiblockState.IDLE)` and `onStructureBroken(...)` calls `setState(StandardMultiblockState.UNFORMED)` automatically - you don't need to set those two yourself.

## `MultiblockStateChangedEvent`

```java
public class MultiblockStateChangedEvent extends Event {
    public MultiblockContext getContext();
    public MultiblockInstance getInstance();
    public MultiblockDefinition getDefinition();
    public ServerLevel getLevel();
    public MultiblockState getPreviousState();
    public MultiblockState getNewState();
}
```

Posted to `NeoForge.EVENT_BUS` (any mod can subscribe, not just the one that owns the definition). **Not cancellable** - by the time this posts, the state has already changed. See also [Callbacks & Events](Callbacks-And-Events.md), which covers the sibling `MultiblockFormedEvent`/`MultiblockBrokenEvent`.

## `MultiblockProgressionTracker`

```java
public class MultiblockProgressionTracker extends SavedData {
    public static MultiblockProgressionTracker get(ServerLevel overworld);
    public void recordStateReached(UUID player, ResourceLocation definitionId, ResourceLocation stateId, long tick);
    public boolean hasReached(UUID player, ResourceLocation definitionId, ResourceLocation stateId /* nullable */);
}
```

Long-term "has this player ever reached this state, and when" memory (per-player/definition/state → last tick). Unlike [`WorldMultiblockTracker`](MultiblockInstance-And-Registry.md#worldmultiblocktracker) (only currently-formed instances), this **survives** the instance being broken and reformed. Always stored on the overworld's `SavedData` regardless of dimension — progression is per-player, not per-dimension.

`stateId = null` in `hasReached(...)` checks "ever reached *any* state" (i.e. formed at least once).

From outside MultiLib, prefer `MultiLibAPI.hasReachedMultiblockState(...)`/`recordMultiblockStateReached(...)`. The [FTB Quests](../Advanced-Features.md#ftb-quests-compatibility) integration deliberately doesn't use this tracker for completion.

## `MultiblockProgressAPI`

```java
public final class MultiblockProgressAPI {
    public static Optional<StructureProgress> compute(ServerLevel level, BlockPos corePos);
}
```

Read-only: reports how complete an **in-progress** structure is, for building your own progress UI without reimplementing matching. Changes nothing.

**Scope:** shaped (`.layer(...)`) structures only — `.pattern(...)`/`.shapeless()` return `Optional.empty()`.

Orientation is detected from blocks already placed around the core (`StructureOrientation.detectFromPlacedBlocks`), falling back to identity (`"Y"`, rotation `0`) when only the bare core exists — safe because with nothing else placed, every orientation needs the same block types and counts, only differing in which positions are flagged missing.

```java
Optional<StructureProgress> progress = MultiblockProgressAPI.compute(serverLevel, corePos);
progress.ifPresent(p -> {
    // p.isComplete(), p.placedCount(), p.missingCount(), p.missingCountsByBlock()
});
```

### `StructureProgress`

```java
public record StructureProgress(int totalRequired, List<MissingBlock> missing) {
    public int missingCount();
    public int placedCount();
    public boolean isComplete();
    public Map<Block, Long> missingCountsByBlock();
}
```

A snapshot, not a live view - computed fresh on each `compute(...)` call, not cached. `missingCountsByBlock()` groups the raw `missing` list into a ready-to-display shopping list keyed by expected `Block` type.

### `MissingBlock`

```java
public record MissingBlock(BlockPos pos, BlockState expectedState) {}
```

One pattern position that isn't correctly filled yet - either still air, or occupied by a block that doesn't match what the pattern expects there.

### `computeDetailed`, `StructureMismatch`, `StructureValidationReport`

```java
public static Optional<StructureValidationReport> computeDetailed(ServerLevel level, BlockPos corePos);
```

Like `compute(...)`, but also reports placed-but-wrong positions — the same MISSING/WRONG/WRONG_STATE classification the ghost overlay computes, as a reusable report. Same shaped-only scope and orientation fallback as `compute(...)`.

```java
public record StructureValidationReport(boolean formed, List<MissingBlock> missing, List<StructureMismatch> mismatches) {
    public int missingCount();
    public int mismatchCount();
}
```

Superset of `StructureProgress`: not just what's missing, but also what's placed but wrong. `formed` is a convenience for "no missing and no mismatched positions", equivalent to what the pattern matcher would report as a success.

```java
public record StructureMismatch(BlockPos pos, char symbol, BlockIngredient expected, BlockState actual, boolean wrongState) {}
```

A placed block that doesn't satisfy the pattern (vs. `MissingBlock`, nothing placed). `wrongState` distinguishes "right block, wrong property" from "different block", via [`BlockIngredient#matchesBlockType`](BlockIngredient.md): if the candidate blocks contain the placed type it's `WRONG_STATE`, else `WRONG`. Mirrors the ghost overlay's `GhostBlockData.Status`, so a wrong-facing block renders distinctly from a completely wrong one.

## See also

- [Block Entity Abstractions § State](BlockEntity-Abstractions.md#state)
- [Block Entity Abstractions § Periodic (re-)validation](BlockEntity-Abstractions.md#periodic-re-validation) - `invalidateStructure()`
- [BlockIngredient § matchesBlockType](BlockIngredient.md) - the `WRONG` vs `WRONG_STATE` distinction `computeDetailed` relies on
- [MultiblockComposition](MultiblockComposition.md) - the equivalent read-only report for an already-**formed** structure
- [Callbacks & Events](Callbacks-And-Events.md)
- [MultiLibAPI](MultiLibAPI.md)
- [Advanced Features § FTB Quests compatibility](../Advanced-Features.md#ftb-quests-compatibility)

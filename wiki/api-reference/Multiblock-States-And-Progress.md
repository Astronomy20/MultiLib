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

Registering the same `id` twice returns the same instance (`computeIfAbsent`), so it's safe to call `register(...)` from a static field initializer that might run more than once. `nameTranslationKey` is optional - set it if you want the state to show up with a readable name somewhere it's picked from a list (currently: the FTB Quests task's "required state" dropdown, see [FTB Quests compatibility](../Advanced-Features.md#ftb-quests-compatibility)).

Registration is only allowed before MultiLib freezes the registry (`FMLLoadCompleteEvent`); register your custom states during your mod's constructor or `FMLCommonSetupEvent`, not lazily at first use, or `register(...)` throws `IllegalStateException`. In practice this means: declare your states as `public static final MultiblockState` fields, same pattern as `StandardMultiblockState` below, and reference the field (or a `touch()`-style no-op) early enough that the class loads before freeze.

Prefer `MultiLibAPI.registerMultiblockState(id[, nameTranslationKey])` from outside MultiLib itself - a thin passthrough, see [MultiLibAPI](MultiLibAPI.md).

## `StandardMultiblockState`

```java
public final class StandardMultiblockState {
    public static final MultiblockState UNFORMED;
    public static final MultiblockState IDLE;
    public static final MultiblockState RUNNING;
    public static final MultiblockState ERROR;
}
```

Four built-in states, all under the `multilib` namespace (`multilib:unformed`, `multilib:idle`, etc.). `AbstractMultiblockControllerBE` uses `UNFORMED`/`IDLE` itself (see below); `RUNNING`/`ERROR` are provided for your own controller to opt into via `setState(...)` but nothing calls them automatically. Note there's no `UNFORMED` → `RUNNING` shortcut enforced anywhere - states are just data, MultiLib doesn't validate transitions between them.

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

Long-term "has this player ever reached this state on this definition, and when" memory (per-player, per-definition, per-state → last-reached tick). Unlike [`WorldMultiblockTracker`](MultiblockInstance-And-Registry.md#worldmultiblocktracker), which only tracks *currently-formed* instances, this record **survives** the underlying instance being broken and reformed. It's always stored on the overworld's `SavedData` regardless of which dimension the multiblock actually formed in, since progression is conceptually per-player, not per-dimension.

Passing `null` as `stateId` to `hasReached(...)` checks "has this player ever reached *any* state for this definition" (i.e. ever formed it at least once), rather than one specific state.

Prefer `MultiLibAPI.hasReachedMultiblockState(...)` / `recordMultiblockStateReached(...)` from outside MultiLib itself - see [MultiLibAPI](MultiLibAPI.md#progression-custom-states). Note the built-in [FTB Quests compatibility](../Advanced-Features.md#ftb-quests-compatibility) deliberately does **not** use this tracker for its own completion check - see that section for why.

## `MultiblockProgressAPI`

```java
public final class MultiblockProgressAPI {
    public static Optional<StructureProgress> compute(ServerLevel level, BlockPos corePos);
}
```

Read-only: reports how complete an **in-progress** (not-yet-formed) structure is, so a consuming mod can render its own progress UI (a progress bar, a "you still need N of block X" shopping list, etc.) without reimplementing pattern matching. Never places, breaks, or otherwise changes anything.

**Scope limitation:** only structures declared with `.layer(...)` (shaped, backed by `ShapedMatcher`) are supported - the same scope `.autoPlace()` already covers. A definition built with `.pattern(PatternProvider)` or `.shapeless()` makes `compute(...)` return `Optional.empty()`.

Orientation for an incomplete structure is detected from whatever's already placed around the core (`StructureOrientation.detectFromPlacedBlocks`); if literally nothing is placed yet besides a bare core, it falls back to the identity orientation (`"Y"`, rotation `0`) - safe because with zero other blocks placed, every orientation needs the same block *types* in the same *quantities*, just at different world positions, so the totals are identical either way (only which positions get flagged "missing" would differ).

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

Like `compute(...)`, but also reports placed-but-wrong positions (not just missing ones) - the same per-position MISSING/WRONG/WRONG_STATE classification the ghost overlay already computes, exposed here as a public, reusable report instead of being stuck inside that event handler. Same scope limitation as `compute(...)`: only `.layer(...)`-declared (shaped) structures are supported, and orientation for an incomplete structure falls back the same way when nothing's placed yet.

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

A placed block that doesn't satisfy the pattern at its position - as opposed to a `MissingBlock` (nothing placed there at all). `wrongState` tells apart "right block, wrong blockstate property" (e.g. facing) from "an entirely different block", determined via [`BlockIngredient#matchesBlockType`](BlockIngredient.md): if the ingredient's candidate blocks contain the placed block's type, it's a `WRONG_STATE` case (right block, wrong orientation/property); otherwise it's a plain `WRONG` (an entirely different block).

This mirrors the `GhostBlockData.Status` enum (`net.astronomy.multilib.network`) used by the client-side ghost overlay, which gained the same `WRONG_STATE` value alongside the pre-existing `MISSING`/`WRONG`/`CORE`/`PLACEABLE` - so a position with the right block but the wrong facing renders distinctly from one with a completely wrong block, instead of both being lumped into a generic "wrong" highlight.

## See also

- [Block Entity Abstractions § State](BlockEntity-Abstractions.md#state)
- [Block Entity Abstractions § Periodic (re-)validation](BlockEntity-Abstractions.md#periodic-re-validation) - `invalidateStructure()`
- [BlockIngredient § matchesBlockType](BlockIngredient.md) - the `WRONG` vs `WRONG_STATE` distinction `computeDetailed` relies on
- [MultiblockComposition](MultiblockComposition.md) - the equivalent read-only report for an already-**formed** structure
- [Callbacks & Events](Callbacks-And-Events.md)
- [MultiLibAPI](MultiLibAPI.md)
- [Advanced Features § FTB Quests compatibility](../Advanced-Features.md#ftb-quests-compatibility)

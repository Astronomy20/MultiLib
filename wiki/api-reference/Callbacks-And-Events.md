[← Back to Home](../index.md)

# Callbacks & Events

Package: `net.astronomy.multilib.api.callback` (callbacks/contexts), `net.astronomy.multilib.api.validation` (`ValidationResult`), `net.astronomy.multilib.api.event` (NeoForge events), `net.astronomy.multilib.api.tool` (`WrenchResult`)

## Lifecycle overview

```
block placed / wrench used
        │
        ▼
  pattern matches ──► validator (optional) ──► MultiblockFormedEvent (cancellable)
                              │ Invalid                    │ cancelled
                              ▼                            ▼
                          (no formation)              (no formation)
                              
                          otherwise: MultiblockInstance created & tracked
                              │
                              ▼
                   every onFormed callback runs
                   core BE.onStructureFormed(...) (if hasCore())
                   every part BE.onJoinedStructure(...)
                              │
              ┌───────────────┼───────────────────┐
              ▼               ▼                   ▼
       onTick (every    onAmbient (every    block broken →
       tick, if set)    N ticks, if set)    MultiblockBrokenEvent
                                            every onBroken callback
                                            core BE.onStructureBroken(...)
                                            every part BE.onLeftStructure()
```

## `MultiblockFormedCallback`

```java
@FunctionalInterface
public interface MultiblockFormedCallback {
    void onFormed(MultiblockFormedContext ctx);
}
```

Set via `MultiblockBuilder.onFormed(cb)` (can be added multiple times - all run, in registration order). Runs **after** the instance is created and tracked, and after `MultiblockFormedEvent` has already passed (not cancelled).

### `MultiblockFormedContext`

```java
public record MultiblockFormedContext(MultiblockContext base) {
    public ServerLevel level();
    public MultiblockInstance instance();
    public MultiblockDefinition definition();
}
```

## `MultiblockBrokenCallback`

```java
@FunctionalInterface
public interface MultiblockBrokenCallback {
    void onBroken(MultiblockBrokenContext ctx);
}
```

Set via `MultiblockBuilder.onBroken(cb)`. Fires when **any** block belonging to a tracked, formed instance is broken - the instance is unregistered from the tracker first, then this callback runs.

### `MultiblockBrokenContext`

```java
public record MultiblockBrokenContext(MultiblockContext base, BlockPos removedPos, BreakReason reason) {
    public enum BreakReason { PLAYER_BREAK, EXPLOSION, REPLACED, UNKNOWN }
    public ServerLevel level();
    public MultiblockInstance instance();
    public MultiblockDefinition definition();
}
```

`removedPos` is the specific block that triggered the break; `reason` distinguishes a normal player break from other paths (periodic-validation-detected removal reports `UNKNOWN`; explosions/replacement are not currently distinguished by any built-in caller beyond the enum's existence - check your NeoForge event source if you need to set these yourself).

## `MultiblockTickCallback`

```java
@FunctionalInterface
public interface MultiblockTickCallback {
    void onTick(MultiblockTickContext ctx);
}
```

Set via `MultiblockBuilder.onTick(cb)` - only one per definition (later calls replace earlier ones). Invoked once per server tick, for **every** tracked, formed instance of this definition, via `WorldMultiblockTracker.tick(...)` (`LevelTickEvent.Post`). Keep this cheap - it runs unconditionally every tick per instance, unlike `onAmbient`.

## `MultiblockAmbientCallback`

```java
@FunctionalInterface
public interface MultiblockAmbientCallback {
    void onAmbient(MultiblockAmbientContext ctx);
}
```

Set via `MultiblockBuilder.onAmbient(cb, intervalTicks)`. Invoked at most every `intervalTicks` ticks per instance (checked lazily against a per-instance last-fired tick counter, not scheduled precisely) - appropriate for particle effects, ambient sounds, or anything that doesn't need per-tick precision.

## `MultiblockValidator`

```java
@FunctionalInterface
public interface MultiblockValidator {
    ValidationResult validate(MultiblockContext ctx);
}
```

Set via `MultiblockBuilder.validator(...)`. Runs **before** the `MultiblockInstance` is created, right after a successful pattern match - can veto formation entirely by returning `ValidationResult.invalid(message)`. The `ctx.instance()` seen by the validator uses a temporary random UUID (not the final instance id) since formation hasn't been committed yet.

### `ValidationResult`

```java
public sealed interface ValidationResult permits Valid, Invalid {
    record Valid() implements ValidationResult {}
    record Invalid(String message, List<BlockPos> problematicPositions) implements ValidationResult {}

    static ValidationResult valid();
    static ValidationResult invalid(String message);
    static ValidationResult invalid(String message, List<BlockPos> positions);
    default boolean isValid();
}
```

## `MultiblockContext`

```java
public record MultiblockContext(ServerLevel level, MultiblockInstance instance, MultiblockDefinition definition) {
    public static MultiblockContext of(ServerLevel level, MultiblockInstance instance);
}
```

The common base every specific `*Context` record wraps (`base()`), giving each specific context the same `level()`/`instance()`/`definition()` accessors without repeating fields.

## NeoForge events

Both are posted to `NeoForge.EVENT_BUS`, so any mod (not just the one that defined the structure) can subscribe - useful for cross-mod integration without depending on a specific callback being registered.

### `MultiblockFormedEvent`

```java
public class MultiblockFormedEvent extends Event implements ICancellableEvent {
    public MultiblockContext getContext();
    public MultiblockInstance getInstance();
    public MultiblockDefinition getDefinition();
    public ServerLevel getLevel();
}
```

**Cancellable.** Posted *before* the instance is tracked and before `onFormed` callbacks run - cancelling it prevents formation entirely (the structure stays "just a pile of blocks" until re-triggered).

### `MultiblockBrokenEvent`

```java
public class MultiblockBrokenEvent extends Event {
    public MultiblockContext getContext();
    public BlockPos getRemovedPos();
    public MultiblockDefinition getDefinition();
    public MultiblockInstance getInstance();
    public ServerLevel getLevel();
}
```

**Not cancellable** - by the time this posts, the block is already broken and the instance already unregistered from the tracker; there's nothing left to veto.

### `MultiblockStateChangedEvent`

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

**Not cancellable.** Posted whenever a formed multiblock's `AbstractMultiblockControllerBE` transitions from one `MultiblockState` to another via `setState(...)` - including the automatic `UNFORMED`→`IDLE` transition on formation. Only fires for multiblocks with a real controller block entity and a resolvable formed instance; a JSON-only multiblock (no controller) never posts this event. See [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md) for the full state lifecycle, including how this event relates to `onStateChanged(...)` and progression tracking.

### `WrenchInteractionEvent`

```java
public class WrenchInteractionEvent extends Event {
    public ServerLevel getLevel();
    public BlockPos getPos();
    @Nullable public ServerPlayer getPlayer();
    public WrenchResult getResult();
}
```

**Not cancellable.** Fired every time a registered wrench (see `IMultiblockWrench`/[`MultiLibAPI.registerWrenchItem(Item)`](MultiLibAPI.md#registerwrenchitemitem-item)) is used on a block - **including** when nothing happens because the clicked block isn't part of any multiblock. MultiLib's own chat feedback for this (`event.WrenchFeedbackHandler`) is gated behind `CommonConfig.DEV_MODE` and off by default - in line with the library's policy of never hardcoding player-facing behavior - so a mod that wants player-facing wrench feedback regardless of dev mode should listen for this event itself (or use the `MultiblockEvents.wrench(...)` KubeJS event for scripts).

`getResult()` returns a `WrenchResult`, a sealed interface with one variant per outcome:

```java
public sealed interface WrenchResult {
    record NotAMultiblock() implements WrenchResult {}
    record AlreadyFormed(MultiblockInstance instance) implements WrenchResult {}
    record ModeDisallowsWrench(MultiblockDefinition definition) implements WrenchResult {}
    record Formed(MultiblockDefinition definition) implements WrenchResult {}
    record FormationFailed(MultiblockDefinition definition, String reason) implements WrenchResult {}
}
```

- `NotAMultiblock` - the clicked block isn't the activation/core block of any registered multiblock.
- `AlreadyFormed` - a multiblock is already formed at this position, so nothing was attempted.
- `ModeDisallowsWrench` - the pattern is actually complete, but this definition's `FormationMode` doesn't allow a wrench to finish it (e.g. `AUTOMATIC`). Only reported once the pattern is confirmed complete - an incomplete structure always reports `FormationFailed` instead, regardless of `FormationMode`, so the wrench stays useful as a "what's missing" diagnostic even on structures that only ever form automatically.
- `Formed` - formation was attempted and succeeded.
- `FormationFailed` - the pattern doesn't match (most common), or it did but something else (e.g. a custom validator) rejected the attempt anyway; `reason` is the pattern matcher's failure summary in the first case, a generic message in the second.

### `MultiblockDefinitionsReloadedEvent`

```java
public class MultiblockDefinitionsReloadedEvent extends Event {
}
```

**Not cancellable.** Fired once, on the server, right after JSON-datapack multiblock definitions finish (re)loading - i.e. after `MultiblockJsonLoader` finishes applying a resource reload, including the very first one on server start, and every subsequent `/reload`. At the point this fires, `MultiblockRegistry` holds the full, current set of definitions for this reload cycle: Java-registered ones (registered earlier, at mod setup) plus whatever JSON datapack definitions this reload just (re)loaded.

Intended for integrations that need to act on the complete registry state per reload rather than per-definition as they're parsed - e.g. re-registering script/dynamic definitions, or patching an existing definition via `MultiLibAPI.redefine(...)`, which requires the target to already be registered. This is what KubeJS's `MultiblockEvents.create(...)`/`modify(...)` hook into - see [KubeJS Integration § When scripts run](../KubeJS-Integration.md#when-scripts-run).

## See also

- [Core Concepts § Activation flow](../Core-Concepts.md#activation-flow)
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md)
- [Block Entity Abstractions](BlockEntity-Abstractions.md) - how `AbstractMultiblockControllerBE` hooks into this same lifecycle
- [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md)

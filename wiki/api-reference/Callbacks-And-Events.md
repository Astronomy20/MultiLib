[← Back to Home](../Home.md)

# Callbacks & Events

Package: `net.astronomy.multilib.api.callback` (callbacks/contexts), `net.astronomy.multilib.api.validation` (`ValidationResult`), `net.astronomy.multilib.api.event` (NeoForge events)

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

Set via `MultiblockBuilder.onFormed(cb)` (can be added multiple times — all run, in registration order). Runs **after** the instance is created and tracked, and after `MultiblockFormedEvent` has already passed (not cancelled).

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

Set via `MultiblockBuilder.onBroken(cb)`. Fires when **any** block belonging to a tracked, formed instance is broken — the instance is unregistered from the tracker first, then this callback runs.

### `MultiblockBrokenContext`

```java
public record MultiblockBrokenContext(MultiblockContext base, BlockPos removedPos, BreakReason reason) {
    public enum BreakReason { PLAYER_BREAK, EXPLOSION, REPLACED, UNKNOWN }
    public ServerLevel level();
    public MultiblockInstance instance();
    public MultiblockDefinition definition();
}
```

`removedPos` is the specific block that triggered the break; `reason` distinguishes a normal player break from other paths (periodic-validation-detected removal reports `UNKNOWN`; explosions/replacement are not currently distinguished by any built-in caller beyond the enum's existence — check your NeoForge event source if you need to set these yourself).

## `MultiblockTickCallback`

```java
@FunctionalInterface
public interface MultiblockTickCallback {
    void onTick(MultiblockTickContext ctx);
}
```

Set via `MultiblockBuilder.onTick(cb)` — only one per definition (later calls replace earlier ones). Invoked once per server tick, for **every** tracked, formed instance of this definition, via `WorldMultiblockTracker.tick(...)` (`LevelTickEvent.Post`). Keep this cheap — it runs unconditionally every tick per instance, unlike `onAmbient`.

## `MultiblockAmbientCallback`

```java
@FunctionalInterface
public interface MultiblockAmbientCallback {
    void onAmbient(MultiblockAmbientContext ctx);
}
```

Set via `MultiblockBuilder.onAmbient(cb, intervalTicks)`. Invoked at most every `intervalTicks` ticks per instance (checked lazily against a per-instance last-fired tick counter, not scheduled precisely) — appropriate for particle effects, ambient sounds, or anything that doesn't need per-tick precision.

## `MultiblockValidator`

```java
@FunctionalInterface
public interface MultiblockValidator {
    ValidationResult validate(MultiblockContext ctx);
}
```

Set via `MultiblockBuilder.validator(...)`. Runs **before** the `MultiblockInstance` is created, right after a successful pattern match — can veto formation entirely by returning `ValidationResult.invalid(message)`. The `ctx.instance()` seen by the validator uses a temporary random UUID (not the final instance id) since formation hasn't been committed yet.

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

Both are posted to `NeoForge.EVENT_BUS`, so any mod (not just the one that defined the structure) can subscribe — useful for cross-mod integration without depending on a specific callback being registered.

### `MultiblockFormedEvent`

```java
public class MultiblockFormedEvent extends Event implements ICancellableEvent {
    public MultiblockContext getContext();
    public MultiblockInstance getInstance();
    public MultiblockDefinition getDefinition();
    public ServerLevel getLevel();
}
```

**Cancellable.** Posted *before* the instance is tracked and before `onFormed` callbacks run — cancelling it prevents formation entirely (the structure stays "just a pile of blocks" until re-triggered).

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

**Not cancellable** — by the time this posts, the block is already broken and the instance already unregistered from the tracker; there's nothing left to veto.

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

**Not cancellable.** Posted whenever a formed multiblock's `AbstractMultiblockControllerBE` transitions from one `MultiblockState` to another via `setState(...)` — including the automatic `UNFORMED`→`IDLE` transition on formation. Only fires for multiblocks with a real controller block entity and a resolvable formed instance; a JSON-only multiblock (no controller) never posts this event. See [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md) for the full state lifecycle, including how this event relates to `onStateChanged(...)` and progression tracking.

## See also

- [Core Concepts § Activation flow](../Core-Concepts.md#activation-flow)
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md)
- [Block Entity Abstractions](BlockEntity-Abstractions.md) — how `AbstractMultiblockControllerBE` hooks into this same lifecycle
- [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md)

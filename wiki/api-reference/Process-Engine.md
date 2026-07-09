[← Back to Home](../index.md)

# Process Engine

Package: `net.astronomy.multilib.api.process`

A reusable state machine for timed machine "jobs" - progress, one-shot input consumption, one-shot output production, pause conditions - the loop every processing multiblock (GregTech, Mekanism, Oritech style) otherwise reimplements. Deliberately agnostic of items/fluids/energy: those live in **your** `ProcessRecipe` implementation; MultiLib provides only the lifecycle.

The processor never ticks itself and holds no static state: you create one per controller block entity and call `tick(...)` from your own tick path (the definition's `onTick` callback or the controller's `serverTick`), which MultiLib already drives.

## `ProcessRecipe`

```java
public interface ProcessRecipe<BE extends BlockEntity, D> {
    int durationTicks();
    boolean canStart(ProcessContext<BE, D> ctx);
    void consumeInputs(ProcessContext<BE, D> ctx);
    void produceOutputs(ProcessContext<BE, D> ctx);
    default boolean canContinue(ProcessContext<BE, D> ctx) { return true; }
}
```

What you supply. `canStart` = "are inputs available"; `canContinue` = per-tick condition (e.g. energy available this tick) - defaults to always true.

## `ProcessContext`

```java
public record ProcessContext<BE extends BlockEntity, D>(ServerLevel level, BE controller, D data) {}
```

Thin carrier handed to every recipe method: the level, your controller BE (typed), and an arbitrary `data` object of your choosing (use `Void`/`null` if you don't need one).

## `RecipeProcessor`

```java
public final class RecipeProcessor<BE extends BlockEntity, D> {
    public enum ProcessState { IDLE, RUNNING, PAUSED }
    public enum PauseBehavior { HOLD, RESET }

    public RecipeProcessor();
    public RecipeProcessor(int pollIntervalTicks);
    public RecipeProcessor<BE, D> pollIntervalTicks(int ticks);
    public RecipeProcessor<BE, D> pauseBehavior(PauseBehavior behavior);
    public RecipeProcessor<BE, D> onAborted(Consumer<ProcessContext<BE, D>> handler);

    public void setRecipe(@Nullable ProcessRecipe<BE, D> recipe);
    public Optional<ProcessRecipe<BE, D>> getRecipe();
    public void setEnabled(boolean enabled);
    public boolean isEnabled();
    public void tick(ProcessContext<BE, D> ctx);
    public void abort();

    public ProcessState getState();
    public int getProgress();
    public int getDurationTicks();
    public float getProgressFraction();

    public void save(CompoundTag tag);
    public void load(CompoundTag tag);
}
```

### Lifecycle contract

- **IDLE** - a recipe must be assigned via `setRecipe`. `canStart` is polled every `pollIntervalTicks` (default 20 - checking item/fluid availability every single tick is wasted work). On success: `consumeInputs` fires **exactly once**, progress resets, state → RUNNING, all within that tick.
- **RUNNING** - each tick checks `canContinue` first. True → progress++; at `progress >= durationTicks()`, `produceOutputs` fires **exactly once** and the processor returns to IDLE. False → the configured `PauseBehavior`:
    - `HOLD` (default): state → PAUSED, progress frozen; resumes counting the tick after `canContinue` turns true again.
    - `RESET`: progress drops to 0 and state returns straight to IDLE - functionally an `abort()`, including firing the `onAborted` hook.
- **`setEnabled(false)`** - hard external gate (wire it to a [`RedstoneControlComponent`](Control-And-Commands.md#redstonecontrolcomponent), a GUI toggle, whatever): while disabled, `tick()` is a complete no-op - no recipe method runs, state and progress freeze as-is. Stricter than HOLD, which still polls `canContinue`.
- **`abort()`** - forces IDLE + progress 0 from any state. Inputs are **not** refunded - refund policy is genuinely a per-mod decision, so it's yours to implement in the `onAborted` hook.

### Persistence caveat

`save`/`load` persist the counters (state, progress, enabled flag) - **not** the recipe, which is an arbitrary object MultiLib can't serialize. After world reload, re-assign the recipe via `setRecipe` (typically in your BE's load path or lazily on first tick); until then, `tick()` on a restored RUNNING/PAUSED processor is a documented no-op that holds state.

Exceptions thrown by your recipe methods propagate out of `tick()`. The framework's tick-callback dispatch catches and logs them (so a buggy recipe can't crash the server tick), but don't rely on that as flow control.

### Usage sketch

```java
public class MyFurnaceBE extends AbstractMultiblockControllerBE {
    public final RecipeProcessor<MyFurnaceBE, Void> processor = new RecipeProcessor<>();
    // setRecipe(...) once, save/load in saveController/loadController
}

MultiLib.define(id)
    // ... layers/keys ...
    .onTick(ctx -> ctx.instance().getCorePos().ifPresent(core -> {
        if (ctx.level().getBlockEntity(core) instanceof MyFurnaceBE be) {
            be.processor.tick(new ProcessContext<>(ctx.level(), be, null));
        }
    }))
    .build();
```

## See also

- [Capability Components](Components.md) - the buffers a recipe's `consumeInputs`/`produceOutputs` usually operate on.
- [Control & Commands](Control-And-Commands.md) - `RedstoneControlComponent.shouldRun(...)` pairs naturally with `setEnabled`.
- [HUD Providers](HUD-Providers.md) - `ProcessHudProvider` shows this processor's progress bar on hover.
- [Callbacks & Events](Callbacks-And-Events.md) - the `onTick` plumbing that drives the processor.

[← Back to Home](../index.md)

# Block Entity Abstractions

Package: `net.astronomy.multilib.api.blockentity`

Optional base classes for structures whose blocks need to react to being part of a formed instance - a controller with state/menu/tick logic, and/or part blocks that need to know their controller.

## `AbstractMultiblockControllerBE`

Base class for the **core**'s block entity. Extends `BlockEntity`.

### State

```java
public MultiblockState getState();
public void setState(MultiblockState newState);
public boolean isFormed(); // state != StandardMultiblockState.UNFORMED
public UUID getInstanceId();
public ResourceLocation getActiveModelId(); // null unless .model(...) is set and the structure is formed
protected ServerLevel getServerLevel();
protected void markDirtyAndSync();
```

`MultiblockState` is a registry-backed value object (`ResourceLocation getId()`), not a fixed enum - `StandardMultiblockState` provides `UNFORMED`/`IDLE`/`RUNNING`/`ERROR`, and you can register your own via `MultiblockStateRegistry`/`MultiLibAPI.registerMultiblockState(...)` (you can't `new` or implement one directly). `setState(...)` is a no-op if the new state equals the current one; otherwise it calls your `onStateChanged(prev, next)` hook, records progression, posts `MultiblockStateChangedEvent`, and syncs to clients - see [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md) for the full lifecycle.

`getActiveModelId()` returns the `ResourceLocation` of the block currently rendered in place of the (hidden) core model - this is what [`MultiblockMasterModelRenderer`](#multiblockmastermodelrenderer) reads every frame. `getServerLevel()`/`markDirtyAndSync()` are small helpers for subclasses that need the owning `ServerLevel` or want to force an NBT save + client sync outside the normal state-change path.

### Hooks you override

```java
protected void onFormed(MultiblockFormedContext ctx) {}
protected void onBroken(MultiblockBrokenContext ctx) {}
protected void onStateChanged(MultiblockState prev, MultiblockState next) {}
protected void serverTick() {}
```

- `onFormed`/`onBroken` - called automatically by the framework's `onStructureFormed`/`onStructureBroken` (see below); you don't call these yourself.
- `serverTick()` - called every tick **only while `isFormed()`**, via the ticker returned by `createServerTicker()`.

### Framework-invoked methods (don't call these - MultiLib calls them for you)

```java
public final void onStructureFormed(MultiblockFormedContext ctx);
public final void onStructureBroken(MultiblockBrokenContext ctx);
```

`onStructureFormed` sets state to `IDLE`, applies the Master-Dummy model-hiding if `.model(...)` is set (hides every part except the core and any `.keepVisible(...)` symbols), then calls your `onFormed(ctx)`. `onStructureBroken` reverses the model-hiding for every remaining tracked position (skipping `ctx.removedPos()`, the block that just broke - it no longer needs its model unhidden), resets to `UNFORMED`, then calls your `onBroken(ctx)`.

### Periodic (re-)validation

```java
public void setValidationInterval(int ticks);
```

Opt-in: when set (`> 0`), the controller periodically checks its own structure:
- **While unformed**: attempts formation via `BlockActivationHandler.triggerFormationAt(...)` - lets a structure be discovered without a fresh block placement or wrench click (e.g. after a `/reload`, or a structure built entirely before the core existed).
- **While formed**: re-validates that every tracked position is still non-air; if any position is found empty, treats it as a break (`BreakReason.UNKNOWN`) and runs the normal broken-lifecycle.

`ExampleControllerBE` sets this to `100` (5 seconds at 20 TPS) as a reference value.

```java
public void invalidateStructure();
```

Marks the structure for (re)validation/formation on the very next server tick, instead of waiting out the rest of `setValidationInterval(...)`'s countdown. For a dev whose own code knows a relevant block changed (e.g. reacting to a neighbor update) and wants the controller to notice immediately rather than up to `validationInterval` ticks later. Safe to call from anywhere, any number of times - it only ever brings the next check closer, never delays it. `setValidationInterval` remains a periodic fallback in case some change bypasses whatever calls `invalidateStructure()`.

### Server ticker

```java
public static <T extends AbstractMultiblockControllerBE> BlockEntityTicker<T> createServerTicker();
```

Return this from your block's `getTicker(...)` to wire up `serverTick()`/periodic validation. Only ticks server-side.

### NBT hooks for subclasses

```java
protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {}
protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {}
```

Override to persist your own controller-specific data - the base class already handles `state`, `instanceId`, and `activeModelId`.

## `AbstractMultiblockControllerBlock`

Base class for the core's `Block`, extends `AbstractMultiblockPartBlock`.

```java
protected abstract InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state);
```

`useWithoutItem(...)` is implemented for you: it checks `isFormed()` on the block entity and only calls your `openMenu(...)` when the structure is actually formed - right-clicking an unformed core does nothing (returns `PASS`), so you don't need to guard against opening a menu for a structure that doesn't exist yet.

## `MultiblockPartMenus`

```java
public final class MultiblockPartMenus {
    public static boolean openPartMenu(ServerPlayer player, ServerLevel level, BlockPos partPos);
    public static boolean openPartMenu(ServerPlayer player, ServerLevel level, MultiblockInstance instance, BlockPos partPos);
}
```

Lets a controller's own menu (opened via `openMenu(...)` above) redirect a player straight to a part block's menu - e.g. a button in the core's GUI for "configure this IO port" instead of making the player walk to it and right-click it themselves. Deliberately just a thin wrapper around vanilla's own `ServerPlayer#openMenu(MenuProvider)`: MultiLib doesn't dictate a GUI/menu framework, so this is a mechanism a dev's own screen/packet handler can call, not a UI of its own.

The plain overload returns `false` (no-op) if there's no loaded block entity at `partPos`, or it doesn't implement `MenuProvider` - e.g. a plain structural block with no GUI of its own. The `MultiblockInstance` overload additionally checks that `partPos` actually belongs to `instance` first - a safety net against a caller passing a stale/unrelated position (e.g. from a client-sent packet) instead of one read fresh off the instance itself - and also returns `false` if that check fails.

## `AbstractMultiblockPartBE`

Base class for **non-core** part block entities that need to track structure membership. Extends `BlockEntity`, implements `IMultiblockPart` via a composed `MultiblockPartComponent`.

```java
protected void savePart(CompoundTag tag, HolderLookup.Provider registries) {}
protected void loadPart(CompoundTag tag, HolderLookup.Provider registries) {}
```

Membership persistence (which instance this part belongs to) is handled automatically; override these two hooks only for your own additional part-specific data.

## `AbstractMultiblockPartBlock`

Base `Block` class enabling the Master-Dummy model-hiding mechanism (`.model(...)` on the definition). Adds a `MODEL_HIDDEN` boolean blockstate property; `getRenderShape(...)` returns `INVISIBLE` when set. Both `AbstractMultiblockControllerBlock` and any plain part block you write should extend this if the structure uses `.model(...)`.

```java
public static void setModelHidden(Level level, BlockPos pos, boolean hidden);
```

Framework-internal, but safe to call if you need to manually force visibility (e.g. a custom debug tool).

## `IMultiblockPart`

Interface implemented by both `AbstractMultiblockPartBE` and (indirectly, for its own purposes) the controller path. Gives any block entity querying access to its structure membership:

```java
public interface IMultiblockPart {
    MultiblockPartComponent getMultiblockComponent();
    default Set<MultiblockAbility<?>> getAbilities() {
        return Set.of();
    }
    default boolean isPartOfStructure();
    default UUID getInstanceId();
    default Optional<MultiblockInstance> getInstance(ServerLevel level);
    default Optional<AbstractMultiblockControllerBE> getController(ServerLevel level);
    default void onJoinedStructure(MultiblockInstance instance) {}
    default void onLeftStructure() {}
}
```

`getController(level)` is the common pattern for a non-core part (e.g. an IO port) to reach its structure's controller for data/energy routing - resolves the instance, then the core position, then the block entity there, only succeeding if it's an `AbstractMultiblockControllerBE`.

`getAbilities()` declares the role(s) this part fulfills within the structure once formed (e.g. an item/energy port), looked up via [`MultiblockAbilities`](MultiblockAbility.md) by whatever code drives the controller's logic. Unlike a fixed 1:1 "this symbol is the IO port", a structure can declare any number of positions with the same ability - the controller just asks for all parts that provide it. Empty by default: most parts (plain structural blocks) provide no ability. See [MultiblockAbility](MultiblockAbility.md) for the full picture.

## `MultiblockPartComponent`

The composable implementation backing `IMultiblockPart` - a small class you can embed in a block entity that can't extend `AbstractMultiblockPartBE` directly (e.g. because it already extends something else). Handles instance-id tracking, model-hiding side effects, and NBT persistence (`saveToTag`/`loadFromTag`) identically to what `AbstractMultiblockPartBE` gives you for free.

## `MultiblockMasterModelRenderer`

The client-side counterpart to the Master-Dummy model swap. A reusable `BlockEntityRenderer` for `.model(...)` structures: it reads the core's `getActiveModelId()` every frame and renders that block's default-state model centered on the core's block-entity position, in place of the core's own (now-hidden, via `MODEL_HIDDEN`) block model. If `getActiveModelId()` is `null`, or the id isn't a registered block, it renders nothing.

```java
public class MultiblockMasterModelRenderer<T extends AbstractMultiblockControllerBE> implements BlockEntityRenderer<T> {
    public MultiblockMasterModelRenderer(BlockEntityRendererProvider.Context context);
}
```

Register it for your controller's `BlockEntityType` exactly like any other block entity renderer:

```java
event.registerBlockEntityRenderer(MY_CONTROLLER_BE, MultiblockMasterModelRenderer::new);
```

Without registering this (or an equivalent renderer that reads `getActiveModelId()`), a `.model(...)` structure will correctly hide its part blocks on formation but show nothing in the core's place.

## Minimal example

```java
public class MyControllerBE extends AbstractMultiblockControllerBE {
    public MyControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setValidationInterval(100);
    }

    @Override protected void onFormed(MultiblockFormedContext ctx) {
        // e.g. initialize an inventory, start a recipe, etc.
    }

    @Override protected void onBroken(MultiblockBrokenContext ctx) {
        // e.g. drop contents
    }

    @Override protected void serverTick() {
        // runs every tick while formed
    }
}
```

```java
public class MyControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {
    @Override protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        // open your menu here - only reached while formed
        return InteractionResult.SUCCESS;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MyControllerBE(MyBlocks.CONTROLLER_BE_TYPE, pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
```

## See also

- [Core Concepts § The controller block-entity pattern](../Core-Concepts.md#the-controller-block-entity-pattern)
- [Callbacks & Events](Callbacks-And-Events.md)
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md)
- [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md)
- [MultiblockAbility](MultiblockAbility.md) - roles a part declares via `IMultiblockPart#getAbilities()`
- [Advanced Features § Master-Dummy model](../Advanced-Features.md#master-dummy-model)

[← Back to Home](../index.md)

# Block Entity Abstractions

Package: `net.astronomy.multilib.api.blockentity`

Optional base classes for structures whose blocks react to being part of a formed instance — a controller with state/menu/tick logic, and part blocks that need to reach their controller.

## `AbstractMultiblockControllerBE`

Base class for the **core**'s block entity (extends `BlockEntity`).

### State

```java
public MultiblockState getState();
public void setState(MultiblockState newState);
public boolean isFormed();               // state != UNFORMED
public UUID getInstanceId();
public ResourceLocation getActiveModelId(); // null unless .model(...) is set and formed
protected ServerLevel getServerLevel();
protected void markDirtyAndSync();
```

`MultiblockState` is registry-backed, not an enum — `StandardMultiblockState` provides `UNFORMED`/`IDLE`/`RUNNING`/`ERROR`; register your own via `MultiLibAPI.registerMultiblockState(...)`. `setState(...)` is a no-op if unchanged; otherwise it calls `onStateChanged`, records progression, posts `MultiblockStateChangedEvent`, and syncs ([lifecycle](Multiblock-States-And-Progress.md)).

`getActiveModelId()` is the block rendered in place of the hidden core — read every frame by [`MultiblockMasterModelRenderer`](#multiblockmastermodelrenderer).

### Hooks you override

```java
protected void onFormed(MultiblockFormedContext ctx) {}
protected void onBroken(MultiblockBrokenContext ctx) {}
protected void onStateChanged(MultiblockState prev, MultiblockState next) {}
protected void serverTick() {}   // every tick while isFormed()
```

`onFormed`/`onBroken` are called by the framework — you don't invoke them.

### Framework-invoked (don't call these)

```java
public final void onStructureFormed(MultiblockFormedContext ctx);
public final void onStructureBroken(MultiblockBrokenContext ctx);
```

`onStructureFormed` sets `IDLE`, applies model-hiding if `.model(...)` is set, then calls your `onFormed`. `onStructureBroken` reverses the hiding for remaining positions (skipping the broken one), resets to `UNFORMED`, then calls your `onBroken`.

### Periodic (re-)validation

```java
public void setValidationInterval(int ticks);
public void invalidateStructure();
```

Opt-in (`> 0`): while **unformed**, periodically attempts formation via `triggerFormationAt(...)` (discovers a structure built before the core, or after `/reload`); while **formed**, re-checks every position is non-air and treats an empty one as a break. `invalidateStructure()` forces the next check onto the next tick — call it when your code knows a relevant block changed. `ExampleControllerBE` uses `100` (5s).

### Server ticker & NBT

```java
public static <T extends AbstractMultiblockControllerBE> BlockEntityTicker<T> createServerTicker();
protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {}
protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {}
```

Return `createServerTicker()` from your block's `getTicker(...)` (ticks server-side only). Override `saveController`/`loadController` for your own data — `state`/`instanceId`/`activeModelId` are already persisted.

## `AbstractMultiblockControllerBlock`

Base for the core's `Block` (extends `AbstractMultiblockPartBlock`).

```java
protected abstract InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state);
```

`useWithoutItem(...)` is implemented for you: it calls `openMenu(...)` only when `isFormed()`, so right-clicking an unformed core does nothing.

## `MultiblockPartMenus`

```java
public static boolean openPartMenu(ServerPlayer player, ServerLevel level, BlockPos partPos);
public static boolean openPartMenu(ServerPlayer player, ServerLevel level, MultiblockInstance instance, BlockPos partPos);
```

Lets a controller's menu redirect a player to a part's menu (e.g. a "configure this port" button). A thin wrapper over vanilla `ServerPlayer#openMenu` — MultiLib dictates no GUI framework. Returns `false` if no block entity at `partPos` or it isn't a `MenuProvider`. The instance overload also verifies `partPos` belongs to `instance` (guards against stale client-sent positions).

## `AbstractMultiblockPartBE`

Base for **non-core** part block entities (extends `BlockEntity`, implements `IMultiblockPart` via `MultiblockPartComponent`).

```java
protected void savePart(CompoundTag tag, HolderLookup.Provider registries) {}
protected void loadPart(CompoundTag tag, HolderLookup.Provider registries) {}
```

Membership persistence is automatic; override these only for your own data.

## `AbstractMultiblockPartBlock`

Base `Block` enabling Master-Dummy model-hiding. Adds a `MODEL_HIDDEN` property; `getRenderShape(...)` returns `INVISIBLE` when set. Extend this for the core and any part block if the structure uses `.model(...)`.

```java
public static void setModelHidden(Level level, BlockPos pos, boolean hidden); // framework-internal
```

## `IMultiblockPart`

Implemented by `AbstractMultiblockPartBE`. Query access to structure membership:

```java
public interface IMultiblockPart {
    MultiblockPartComponent getMultiblockComponent();
    default Set<MultiblockAbility<?>> getAbilities() { return Set.of(); }
    default boolean isPartOfStructure();
    default UUID getInstanceId();
    default Optional<MultiblockInstance> getInstance(ServerLevel level);
    default Optional<AbstractMultiblockControllerBE> getController(ServerLevel level);
    default void onJoinedStructure(MultiblockInstance instance) {}
    default void onLeftStructure() {}
}
```

`getController(level)` is how a part (e.g. an IO port) reaches its controller for routing. `getAbilities()` declares the role(s) this part fulfills, looked up via [`MultiblockAbilities`](MultiblockAbility.md); empty by default. A structure can have any number of parts with the same ability.

## `MultiblockPartComponent`

The composable backing for `IMultiblockPart` — embed it in a block entity that can't extend `AbstractMultiblockPartBE`. Handles instance-id tracking, model-hiding, and NBT (`saveToTag`/`loadFromTag`).

## `MultiblockMasterModelRenderer`

Client-side counterpart to the model swap. A `BlockEntityRenderer` that reads the core's `getActiveModelId()` each frame and renders that block's model in place of the hidden core. Renders nothing if the id is null or unregistered.

```java
public MultiblockMasterModelRenderer(BlockEntityRendererProvider.Context context);
```

Register it like any BE renderer — without it, a `.model(...)` structure hides its parts but shows nothing:

```java
event.registerBlockEntityRenderer(MY_CONTROLLER_BE, MultiblockMasterModelRenderer::new);
```

## Minimal example

```java
public class MyControllerBE extends AbstractMultiblockControllerBE {
    public MyControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setValidationInterval(100);
    }
    @Override protected void onFormed(MultiblockFormedContext ctx) { }
    @Override protected void onBroken(MultiblockBrokenContext ctx) { }
    @Override protected void serverTick() { }   // every tick while formed
}

public class MyControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {
    @Override protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        return InteractionResult.SUCCESS;        // only reached while formed
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MyControllerBE(MyBlocks.CONTROLLER_BE_TYPE, pos, state);
    }
    @Override @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
```

## See also

- [Core Concepts § The controller block-entity pattern](../Core-Concepts.md#the-controller-block-entity-pattern)
- [Callbacks & Events](Callbacks-And-Events.md), [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md), [Multiblock States & Progress](Multiblock-States-And-Progress.md), [MultiblockAbility](MultiblockAbility.md), [Master-Dummy model](../Advanced-Features.md#master-dummy-model)

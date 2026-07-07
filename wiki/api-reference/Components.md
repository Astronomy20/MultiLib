[← Back to Home](../index.md)

# Capability Components

Package: `net.astronomy.multilib.api.component`

Ready-made energy/fluid/item buffers for controller block entities, plus a content cache that lets buffer contents survive an unform/reform cycle. These are the pieces every machine mod otherwise rewrites: a buffer with change notification, NBT persistence, and a capability registration one-liner. Mechanism only - the components never touch chat, sounds, or GUIs.

All three buffers follow the same pattern: an optional `Runnable onChanged` fires on every real (non-simulated) content change, so the owning block entity can pass `this::setChanged` and stay chunk-dirty automatically.

## `EnergyBufferComponent`

```java
public class EnergyBufferComponent extends EnergyStorage {
    public EnergyBufferComponent(int capacity, int maxReceive, int maxExtract);
    public EnergyBufferComponent(int capacity, int maxReceive, int maxExtract, @Nullable Runnable onChanged);
    public void setOnChanged(@Nullable Runnable onChanged);
    public void save(CompoundTag tag);
    public void load(CompoundTag tag);
}
```

A NeoForge `IEnergyStorage` (FE). `save`/`load` persist only the stored amount - capacity and transfer limits are code-side configuration, so changing them in a mod update never fights stale NBT.

## `FluidTankComponent`

```java
public class FluidTankComponent extends FluidTank {
    public FluidTankComponent(int capacity);
    public FluidTankComponent(int capacity, @Nullable Predicate<FluidStack> validator);
    public FluidTankComponent(int capacity, @Nullable Predicate<FluidStack> validator, @Nullable Runnable onChanged);
    public void setOnChanged(@Nullable Runnable onChanged);
    public void save(CompoundTag tag, HolderLookup.Provider registries);
    public void load(CompoundTag tag, HolderLookup.Provider registries);
}
```

A NeoForge `FluidTank` with an optional fluid validator. Note the extra `HolderLookup.Provider` on `save`/`load`: `FluidStack` NBT encoding needs registry access on MC 1.21.1 - pass the `registries` argument your block entity's own save/load hooks already receive.

## `ItemBufferComponent`

```java
public class ItemBufferComponent extends ItemStackHandler {
    public ItemBufferComponent(int slots);
    public ItemBufferComponent(int slots, @Nullable BiPredicate<Integer, ItemStack> validator);
    public ItemBufferComponent(int slots, @Nullable BiPredicate<Integer, ItemStack> validator, @Nullable Runnable onChanged);
    public void setOnChanged(@Nullable Runnable onChanged);
    public void setSlotValidator(@Nullable BiPredicate<Integer, ItemStack> validator);
    public void save(CompoundTag tag, HolderLookup.Provider registries);
    public void load(CompoundTag tag, HolderLookup.Provider registries);
}
```

A NeoForge `ItemStackHandler` with an optional per-slot `(slot, stack)` validator. Same `HolderLookup.Provider` requirement as the fluid tank.

## `MultiblockComponentHelper`

```java
public final class MultiblockComponentHelper {
    public static <BE extends BlockEntity> void registerEnergy(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IEnergyStorage> getter);
    public static <BE extends BlockEntity> void registerFluid(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IFluidHandler> getter);
    public static <BE extends BlockEntity> void registerItem(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IItemHandler> getter);
}
```

The capability wiring. MultiLib cannot auto-register - it has no visibility into *your* block entity types - so you call these once from your own `RegisterCapabilitiesEvent` listener:

```java
public class MyControllerBE extends AbstractMultiblockControllerBE {
    public final EnergyBufferComponent energy = new EnergyBufferComponent(100_000, 1_000, 1_000, this::setChanged);

    @Override
    protected void saveController(CompoundTag tag, HolderLookup.Provider registries) { energy.save(tag); }
    @Override
    protected void loadController(CompoundTag tag, HolderLookup.Provider registries) { energy.load(tag); }
}

@SubscribeEvent
public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
    MultiblockComponentHelper.registerEnergy(event, MY_CONTROLLER_BE_TYPE, be -> be.energy);
}
```

`saveController`/`loadController` are the protected extension points [`AbstractMultiblockControllerBE`](BlockEntity-Abstractions.md) already provides - no `saveAdditional` override needed. `ExampleControllerBE`/`ExampleSetup` in the source tree carry exactly this wiring as a working reference.

## `ContentCache`

```java
public final class ContentCache {
    public record Slot(String key, Consumer<CompoundTag> save, Consumer<CompoundTag> load) {}
    public static Slot slot(String key, Consumer<CompoundTag> save, Consumer<CompoundTag> load);
    public static CompoundTag snapshot(Slot... slots);
    public static void restore(CompoundTag tag, Slot... slots);
}
```

Mekanism-style content survival: snapshot the state of any set of components when the structure unforms, restore it when it reforms. Each `Slot` is a named save/load pair; `snapshot` nests each slot's tag under its key, `restore` skips keys missing from the tag instead of force-resetting - so adding a new slot in a mod update never wipes old snapshots. Keyed (not positional) on purpose: reordering slots between versions is harmless.

Where the snapshot tag lives between unform and reform is deliberately your call (a field on your controller BE, its NBT, wherever):

```java
.onBroken(ctx -> myBE.cachedContents = ContentCache.snapshot(
        ContentCache.slot("energy", myBE.energy::save, myBE.energy::load)))
.onFormed(ctx -> {
    if (myBE.cachedContents != null) {
        ContentCache.restore(myBE.cachedContents,
                ContentCache.slot("energy", myBE.energy::save, myBE.energy::load));
    }
})
```

For fluid/item slots that need registry access, capture it from the callback: `tag -> myBE.tank.save(tag, ctx.level().registryAccess())`.

## See also

- [Block Entity Abstractions](BlockEntity-Abstractions.md) - the `saveController`/`loadController` hooks these components plug into.
- [Ports](Ports.md) - expose these buffers on dedicated port blocks instead of the controller itself.
- [HUD Providers](HUD-Providers.md) - `EnergyHudProvider`/`FluidHudProvider` show these buffers on hover via Jade/TOP.
- [Callbacks & Events](Callbacks-And-Events.md) - the `onFormed`/`onBroken` hooks `ContentCache` pairs with.

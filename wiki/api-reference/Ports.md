[← Back to Home](../index.md)

# Ports (Hatches)

Package: `net.astronomy.multilib.api.port`

Base classes for **port blocks**: structure members that expose the controller's capabilities to the outside world, GregTech-hatch / Mekanism-valve style. Pipes and cables connect to the port; the actual buffer lives on the controller. The proxy is fully generic over `BlockCapability` - it is deliberately not tied to the [component classes](Components.md) or even to `AbstractMultiblockControllerBE`, so any controller block entity works.

Ports never tick. Everything resolves lazily at capability-query time, and a port that is not currently part of a formed structure exposes nothing (`Optional.empty()` / `null` capability).

## `AbstractPortBlockEntity`

```java
public abstract class AbstractPortBlockEntity extends AbstractMultiblockPartBE {
    public Optional<BlockEntity> getController();
    public <C extends BlockEntity> Optional<C> getController(Class<C> type);
    public <T> Optional<T> getControllerCapability(BlockCapability<T, Direction> cap, @Nullable Direction side);
}
```

Extends the existing part mechanism (`AbstractMultiblockPartBE`), so joining/leaving structures is already wired through the framework's `onJoinedStructure`/`onLeftStructure` - the port overrides them to track the structure's controller position, persisted to NBT so it survives chunk reloads.

- `getController()` - the controller's block entity, empty when unformed, the controller chunk is unloaded, or no BE exists there. The resolved reference is cached by position and invalidated on structure leave.
- `getController(Class)` - convenience typed variant for when you know your controller class.
- `getControllerCapability(cap, side)` - the generic proxy: resolves `cap` **against the controller's position** via `Level.getCapability`. Guards against self-recursion (a port never proxies back to itself).

## `AbstractPortBlock`

```java
public abstract class AbstractPortBlock extends AbstractMultiblockPartBlock implements EntityBlock {
}
```

Minimal `EntityBlock` base - implement `newBlockEntity` in your concrete subclass, exactly like controller blocks do with `AbstractMultiblockControllerBlock`.

## `PortCapabilityHelper`

```java
public final class PortCapabilityHelper {
    public static <T, BE extends AbstractPortBlockEntity> void registerProxy(
            RegisterCapabilitiesEvent event, BlockCapability<T, Direction> cap, BlockEntityType<BE> type);
}
```

Registers the forwarding: any capability query of `cap` against a block entity of `type` is redirected to `getControllerCapability`. Call once per capability you want the port to forward.

## Quickstart

1. Extend `AbstractPortBlockEntity` for your port's block entity.
2. Extend `AbstractPortBlock`, implementing `newBlockEntity`.
3. Give the port its own symbol in the multiblock pattern (optionally also tag it with a [`MultiblockAbility`](MultiblockAbility.md) if controller logic needs to find ports by role - unrelated to the capability forwarding itself).
4. In your `RegisterCapabilitiesEvent` listener:
   ```java
   PortCapabilityHelper.registerProxy(event, Capabilities.ItemHandler.BLOCK, MY_PORT_BE_TYPE);
   PortCapabilityHelper.registerProxy(event, Capabilities.EnergyStorage.BLOCK, MY_PORT_BE_TYPE);
   ```
5. External pipes/cables querying those capabilities against the port are now transparently served by the controller.

## Relation to `ioPort()`

[`MultiLibAPI.block(block).ioPort()`](../Advanced-Features.md#io-ports) already forwards capabilities from a plain tracked block to the controller, with zero classes to write. Choose the port base classes instead when the port needs its **own block entity**: a persistent controller link that survives chunk reloads, typed `getController(Class)` access, or any custom per-port behavior/state. The two mechanisms coexist - they solve the same routing problem at different levels of ceremony.

## See also

- [Capability Components](Components.md) - the buffers a controller typically exposes through its ports.
- [Block Entity Abstractions](BlockEntity-Abstractions.md) - the part/controller base classes this builds on.
- [MultiblockAbility](MultiblockAbility.md) - role-tagging parts so the controller can enumerate them.

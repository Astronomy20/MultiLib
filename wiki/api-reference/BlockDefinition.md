[← Back to Home](../Home.md)

# BlockDefinition & BlockDefinitionBuilder

Package: `net.astronomy.multilib.api.block`

Block-level multiblock metadata — properties of a `Block` itself, independent of any single `MultiblockDefinition`. Accessed via `MultiLibAPI.block(block)`.

## Why this exists separately from `MultiblockDefinition`

A structure's `MultiblockBuilder` describes one specific pattern. Some facts, though, are really about the *block*, not any one pattern using it: "this block is always the core of multiblock X," "this block always has its own facing," "this block should preserve its inventory when a multiblock is dismantled." `BlockDefinitionBuilder` captures those once, on the block, so every structure that reuses the block benefits automatically.

## `BlockDefinitionBuilder`

```java
public final class BlockDefinitionBuilder {
    public BlockDefinitionBuilder core(ResourceLocation... multiblockIds);
    public BlockDefinitionBuilder wallSharing(boolean enabled);
    public BlockDefinitionBuilder ioPort();
    public BlockDefinitionBuilder dropOriginalOnBreak();
    public BlockDefinitionBuilder mainFace();
    public BlockDefinition build();
}
```

### `core(ResourceLocation... multiblockIds)`
Declares this block as the core of one or more multiblocks, by id. When that multiblock's own `MultiblockBuilder` doesn't call `.core(char)`, the symbol mapped to this block is **auto-assigned** as the core symbol at build time. If the multiblock's builder explicitly declares a *different* block as core, registration of that multiblock fails with a logged error (game load continues; only that one structure ends up unregistered).

### `wallSharing(boolean enabled)`
Overrides wall sharing for this block **specifically when used as a core/activation symbol** (disabled by default for those roles). Has no effect on non-core usages of the block — those already go through the existing priority chain (symbol override → block declaration → definition flag → default). See [Advanced Features § Wall sharing](../Advanced-Features.md#wall-sharing).

### `ioPort()`
Marks this block as an IO port: item/fluid/energy capability requests on it are automatically forwarded to the controller block entity of whatever multiblock instance it's currently part of (via `IOPortCapabilityHandler`).

### `dropOriginalOnBreak()`
When a multiblock containing this block is dismantled, this block keeps its block entity data and drops normally (preserving NBT/inventory via vanilla drop logic) instead of being wiped to a clean copy of itself. See `BlockBreakHandler.dismantleRemainingBlocks(...)`.

### `mainFace()`
Marks this block as having a meaningful placed facing of its own (a `FACING`/`HORIZONTAL_FACING` blockstate property, like a furnace). When used as a multiblock's core, the ghost overlay and auto-place preview orientation is pinned to the block's **actual in-world facing** instead of following the player's look direction. See the [Directional Cores Guide](../Directional-Cores-Guide.md).

### `build()`
Constructs the `BlockDefinition` and registers it into `BlockDefinitionRegistry`. Unlike `MultiblockBuilder.build()`, there's no validation step that can silently skip registration here.

## `BlockDefinition`

The built, immutable result.

```java
public final class BlockDefinition {
    public Block getBlock();
    public Set<ResourceLocation> getCoreOfMultiblocks();
    public boolean isCoreOf(ResourceLocation multiblockId);
    public boolean declaresCore();
    public Optional<Boolean> getWallSharingOverride();
    public boolean isIoPort();
    public boolean isDropOriginalOnBreak();
    public boolean hasMainFace();
}
```

## Example

```java
MultiLibAPI.block(MyBlocks.REACTOR_CORE)
        .core(ResourceLocation.fromNamespaceAndPath("examplemod", "reactor"))
        .mainFace()
        .ioPort()
        .build();
```

With this declared, the `examplemod:reactor` `MultiblockBuilder` doesn't need to call `.core(...)` at all (as long as only one symbol maps to `REACTOR_CORE`) — and the ghost overlay for that structure will always preview in the direction the core block is actually facing, not the direction the player is looking.

## See also

- [MultiLibAPI § block(Block)](MultiLibAPI.md#blockblock-block)
- [Directional Cores Guide](../Directional-Cores-Guide.md)
- [Advanced Features § Wall sharing, IO ports](../Advanced-Features.md)

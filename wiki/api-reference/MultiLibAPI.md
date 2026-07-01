[← Back to Home](../Home.md)

# `MultiLibAPI`

Package: `net.astronomy.multilib.api`

The public entry point for mods wanting to declare multiblock structures and block-level metadata. Prefer this class over touching `MultiblockRegistry`/`BlockDefinitionRegistry` directly — it's the intended integration surface.

## Methods

### `define(ResourceLocation id)`

```java
public static MultiblockBuilder define(ResourceLocation id)
```

Starts a new [`MultiblockBuilder`](MultiblockBuilder.md) for the structure identified by `id` (equivalent to `new MultiblockBuilder().id(id)`). `id` is what recipe-browser integrations, JSON overrides, wrench diagnostics, and `getDefinition(id)` use to reference this structure.

```java
MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("examplemod", "my_altar"))
        .layer("PPP", " P ", " G ")
        .key('P', BlockIngredient.of(Blocks.STONE_BRICKS))
        .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
        .core('G')
        .build();
```

---

### `block(Block block)`

```java
public static BlockDefinitionBuilder block(Block block)
```

Entry point for declaring **block-level** multiblock metadata — properties of the `Block` itself, independent of any single structure: `.core(ids...)`, `.ioPort()`, `.dropOriginalOnBreak()`, `.mainFace()`, `.wallSharing(enabled)`. See [BlockDefinition reference](BlockDefinition.md).

```java
MultiLibAPI.block(MyBlocks.CONTROLLER_BLOCK).mainFace().build();
```

---

### `getDefinition(ResourceLocation id)`

```java
public static Optional<MultiblockDefinition> getDefinition(ResourceLocation id)
```

Looks up a registered [`MultiblockDefinition`](MultiblockDefinition.md) by id.

---

### `getAllDefinitions()`

```java
public static Collection<MultiblockDefinition> getAllDefinitions()
```

Returns every currently registered definition (from any mod), including ones loaded from JSON/datapacks. Useful for diagnostics or listing "everything buildable."

---

### `setWallSharingMode(Block block, WallSharingMode mode)` / `getRegisteredWallSharingMode(Block block)`

```java
public static void setWallSharingMode(Block block, WallSharingMode mode)
public static Optional<WallSharingMode> getRegisteredWallSharingMode(Block block)
```

Registers (or looks up) a default wall-sharing mode for a block across *all* structures that use it as a non-core/non-activation symbol, without needing `IWallSharable` or a per-structure `.key(symbol, ingredient, mode)` override. Consulted by `MultiblockDefinition.getWallSharingMode(char)` as one step in the override priority chain — see [Advanced Features § Wall sharing](../Advanced-Features.md#wall-sharing).

## See also

- [MultiblockBuilder](MultiblockBuilder.md)
- [BlockDefinition](BlockDefinition.md)
- [Getting Started](../Getting-Started.md)

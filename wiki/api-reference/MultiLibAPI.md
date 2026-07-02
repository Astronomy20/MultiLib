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

## Progression & custom states

See [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md) for the full picture — these four methods are thin passthroughs to `MultiblockProgressionTracker` / `MultiblockStateRegistry`.

### `hasReachedMultiblockState(...)`

```java
public static boolean hasReachedMultiblockState(UUID player, MinecraftServer server, ResourceLocation definitionId, @Nullable ResourceLocation stateId)
public static boolean hasReachedMultiblockState(ServerPlayer player, ResourceLocation definitionId, @Nullable ResourceLocation stateId)
```

True if `player` has ever driven an instance of `definitionId` to `stateId`. If `stateId` is `null`, true if the player has ever formed the structure at least once (equivalent to asking whether `IDLE` was reached). The `ServerPlayer` overload is a convenience for callers (e.g. FTB Quests task `canSubmit`) that already hold a player and don't want to fish out a `MinecraftServer` themselves.

### `recordMultiblockStateReached(ServerPlayer player, ResourceLocation definitionId, ResourceLocation stateId)`

```java
public static void recordMultiblockStateReached(ServerPlayer player, ResourceLocation definitionId, ResourceLocation stateId)
```

Manually records that `player` has driven `definitionId` to `stateId`. Normal use is automatic — MultiLib calls this internally on formation and on every `AbstractMultiblockControllerBE.setState(...)`. Call it by hand only for a condition not representable by a simple `MultiblockState` (e.g. "reactor stable for 5 minutes") from your own controller block entity.

### `registerMultiblockState(ResourceLocation id[, String nameTranslationKey])`

```java
public static MultiblockState registerMultiblockState(ResourceLocation id)
public static MultiblockState registerMultiblockState(ResourceLocation id, String nameTranslationKey)
```

Passthrough to [`MultiblockStateRegistry`](Multiblock-States-And-Progress.md#multiblockstateregistry) — the single point from which mod developers register custom states beyond the four built into [`StandardMultiblockState`](Multiblock-States-And-Progress.md#standardmultiblockstate). Must be called before MultiLib freezes the registry (`FMLLoadCompleteEvent`) — register during your mod's constructor or `FMLCommonSetupEvent`.

## See also

- [MultiblockBuilder](MultiblockBuilder.md)
- [BlockDefinition](BlockDefinition.md)
- [Multiblock States & Progress Tracking](Multiblock-States-And-Progress.md)
- [Getting Started](../Getting-Started.md)

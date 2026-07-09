[← Back to Home](index.md)

# Advanced Features

Systems beyond the shaped-pattern-plus-callbacks flow of [Core Concepts](Core-Concepts.md). Every heading here is linked from elsewhere in the wiki.

## Shapeless structures

`.shapeless()` switches from a fixed grid to a **flood-fill** match (`ShapelessMatcher`): it fills outward from the placed block (6-directional, stopping at air) up to `.maxSize(x, y, z)` (default `64,64,64`), then validates the blob.

```java
MultiLib.define(id)
        .shapeless()
        .minSize(3, 3, 3).maxSize(9, 9, 9)
        .shell(BlockIngredient.of(Blocks.IRON_BLOCK))
        .interior(BlockIngredient.any())
        .require(BlockIngredient.of(Blocks.DIAMOND_BLOCK), 1, 4)
        .core('O') // still needs a core symbol reachable by the flood-fill
        .build();
```

Shapeless structures don't rotate — `getTransform()` always reports `TransformData(0, false, "NONE")`.

## Shell/interior matching

MultiLib classifies each flood-filled block as **shell** (touches the bounding box on ≥1 axis) or **interior** (doesn't):

- `.shell(ingredient)` — required for every shell block, unless a face override applies.
- `.shellFace(Direction, ingredient)` — per-face override (e.g. a different top).
- `.interior(ingredient)` — required for every strictly-interior block.
- `.require(ingredient, min, max)` — count constraint across the whole region (shell + interior).

Shapeless-only; no effect on shaped definitions.

## Procedural patterns (`PatternProvider`)

`.pattern(PatternProvider)` computes a shape instead of typing it out:

```java
@FunctionalInterface
public interface PatternProvider {
    @Nullable BlockIngredient getIngredientAt(int x, int y, int z);
    default Vec3i getSize() { return new Vec3i(1, 1, 1); }
}
```

`getIngredientAt` returns `null` for an empty cell; `getSize()` is the provider's bounding box, overridable with `.boundingBox(x, y, z)`. `FunctionalMatcher` searches these with the same rotation machinery as shaped patterns ([details](Rotation-And-Matching.md)).

Built-in providers (`net.astronomy.multilib.api.pattern.providers`):

| Provider | Shape |
|---|---|
| `SphereProvider(radius, ingredient)` | Solid sphere |
| `HollowSphereProvider(radius, ingredient)` | One-block-thick sphere shell |
| `CylinderProvider(radius, height, ingredient)` | Solid cylinder along Y |
| `HollowCubeProvider(width, height, depth, shell, interior)` | Cube with a shell and optional interior (`null` = unconstrained) |
| `PyramidProvider(baseSize, ingredient)` | Stepped pyramid, shrinking 2 per Y step |
| `LayeredPatternProvider(layers, blockMap)` | Wraps a text grid as a provider — what `.layer(...)` uses internally |

Implement `PatternProvider` yourself for anything else (ellipsoid, maze, …) — it's a single-method interface.

## JSON/datapack definitions

Structures can live in datapacks under `data/<namespace>/multiblocks/<name>.json`, loaded by `MultiblockJsonLoader` and reloaded cleanly on `/reload` without touching Java definitions.

Top-level fields (all optional except `layers`+`keys` or `pattern`):

```json
{
  "layers": [["PPP", " P ", " G "], ["POP", " P ", " G "]],
  "keys": {
    "P": { "block": "minecraft:stone_bricks" },
    "O": { "block": "examplemod:controller" },
    "G": "minecraft:gold_block"
  },
  "activation": "O",
  "core": "O",
  "rotations": "horizontal",
  "formation_mode": "automatic_and_wrench",
  "priority": 0,
  "require_air_in_empty_positions": false,
  "wall_sharing": true,
  "no_wall_sharing": ["O"],
  "optional": ["G"],
  "shapeless": false,
  "min_size": [3, 3, 3],
  "max_size": [9, 9, 9],
  "shell": { "tag": "examplemod:reactor_shell" },
  "interior": { "any": true },
  "pattern": { "type": "multilib:sphere", "radius": 4, "ingredient": { "block": "minecraft:iron_block" } },
  "on_formed_sound": "minecraft:block.beacon.activate",
  "on_broken_sound": "minecraft:block.beacon.deactivate",
  "icon": "examplemod:controller"
}
```

`keys` entries accept a bare block id string or a full ingredient object:

| JSON | `BlockIngredient` |
|---|---|
| `"minecraft:stone"` or `{"block": "minecraft:stone"}` | `.of(...)` |
| `{"block": "...", "properties": {...}}` | `.ofState(...)` — ⚠️ property parsing is a stub; a `properties` key logs a warning and falls back to `of(...)` |
| `{"tag": "examplemod:some_tag"}` | `.tag(...)` |
| `{"any_of": [...]}` | `.anyOf(...)` |
| `{"any": true}` | `.any()` |
| `{"type": "...", ...}` | A custom ingredient via `MultiblockSerializers.registerIngredient(...)` |

`pattern.type` supports the five built-in providers (`multilib:sphere`/`cylinder`/`hollow_sphere`/`hollow_cube`/`pyramid`) plus any custom `PatternProviderSerializer`.

An optional `"variants"` array declares [several geometries under one id](api-reference/MultiblockBuilder.md#variants) — mutually exclusive with a top-level `"layers"` (the loader rejects both). Each entry has a `"name"`, its own `"layers"`, and an optional `"keys"` overriding the shared top-level keys for that variant. Entries are tried in order:

```json
{
  "keys": { "I": "minecraft:iron_block", "L": "minecraft:lapis_block" },
  "core": "L",
  "variants": [
    { "name": "tall", "layers": [["III"], ["ILI"]] },
    { "name": "compact", "layers": ["ILI"] }
  ]
}
```

Java-only (not expressible in JSON): arbitrary `onFormed`/`onBroken` logic (beyond the sound hooks), `.validator(...)`, `.onTick(...)`/`.onAmbient(...)`, `.freeBlock(...)`, `.optionalLayer(...)`, geometry constraints, `.model(...)`/`.keepVisible(...)`.

## Master-Dummy model

`.model(ResourceLocation)` + `.keepVisible(char...)` gives a formed structure a single-block look: on formation, every part block hides (a `MODEL_HIDDEN` property) except the core and kept-visible symbols, and the core renders `modelId` in their place. Hitboxes are unaffected — purely visual.

Requirements: the core extends `AbstractMultiblockControllerBlock`, parts extend `AbstractMultiblockPartBlock`. Hiding/unhiding is automatic via `onStructureFormed`/`onStructureBroken`. See [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md).

## Wall sharing

Non-core symbols can share a block with an adjacent structure, but this is **opt-in**. `getWallSharingMode(char)` resolves per symbol, highest priority first:

1. **Symbol-level** — `.key(symbol, ingredient, mode)` or `.noWallSharing(...)`.
2. **Block-level** — `IWallSharable.getDefaultWallSharingMode()` or `MultiLib.block(block).wallSharing(...)`.
3. **Definition-level** — `.wallSharing(boolean)`, `false` unless set.
4. **Fallback** — ordinary symbols follow the definition default; the core/activation symbol always defaults to `DISABLED` unless explicitly overridden.

`WallSharingMode`: `ENABLED`, `DISABLED`, `INHERIT` (defers to the next link).

## Auto-place

`.autoPlace()` enables Ctrl+Right-click auto-building: clicking an unformed core with the modifier held scans each fixed-grid cell, and for every missing cell whose expected block the player holds, places it — consuming one item (free in creative). It then attempts formation, so a stocked player completes a structure in one click. An action-bar message reports blocks placed and still missing.

The modifier key defaults to Left Ctrl and isn't a Controls-menu keybind; a consuming mod can rebind it via [`MultiLibClient.setAutoPlaceModifierKey(...)`](api-reference/MultiLibClient.md#auto-place-modifier-key). Its repeat speed is tunable in [config](Configuration.md#commonconfig-configmultilibcommontoml) (`autoPlaceSpeedHeldItem`/`autoPlaceSpeedEmptyHand`).

## Ghost overlay

Independent of auto-place: interacting with an unformed **core** shows a client-side ghost overlay — translucent renders at every missing/mismatched position, refreshed every 10 ticks, auto-expiring after a configurable duration. Clicking a horizontal face cycles the previewed yaw; clicking again toggles single-layer vs. whole-structure view. `.ghostOverlayDebug()` adds a per-frame render-time chat line (dev only).

With `.mainFace()` on the core's `BlockDefinition` ([Directional Cores Guide](Directional-Cores-Guide.md)), the preview pins to the core's actual facing instead of the player's.

## IO ports

`MultiLib.block(block).ioPort().build()` marks a block whose item/fluid/energy capability requests are forwarded to the controller of whichever instance contains it (`IOPortCapabilityHandler`). A small "input/output" block anywhere in a structure then proxies the controller's handlers, with no forwarding code of your own. For a port with its own block entity, see [Ports](api-reference/Ports.md).

## Built-in machine toolkit

The building blocks a processing machine needs, each with its own reference page:

- [Capability components](api-reference/Components.md) — energy/fluid/item buffers with change hooks, NBT, and one-line registration; plus `ContentCache` for content survival across unform/reform.
- [Ports (hatches)](api-reference/Ports.md) — base classes for port blocks with their own block entity (persistent controller link, typed access); the richer counterpart to `ioPort()` above.
- [Process engine](api-reference/Process-Engine.md) — a job state machine (progress, one-shot input/output, pause conditions) driven from your tick callback.
- [Control helpers & commands](api-reference/Control-And-Commands.md) — redstone modes, comparator scaling, ownership, `/multilib`.
- [HUD providers](api-reference/HUD-Providers.md) — Jade/The One Probe hover-info from one viewer-agnostic API.
- [Pattern variants](api-reference/MultiblockBuilder.md#variants) — several geometries under one id, with in-place wrench upgrades.

All opt-in and mechanism-only — nothing is player-facing unless your mod makes it so.

## Wrench tool

A "wrench" is any `Item` implementing `IMultiblockWrench` (`useOn(UseOnContext)`). **MultiLib ships none** — implement it on your own tool. `ExampleWrenchItem` is a reference that reports "not a multiblock", the formed `MultiblockState`, or attempts formation (respecting `FormationMode.allowsWrench()`) and reports the failure summary otherwise. Only `WRENCH`/`AUTOMATIC_AND_WRENCH` structures form this way ([formation modes](Core-Concepts.md#formation-modes)).

## JEI / REI / EMI / Patchouli / GuideME / FTB Quests compatibility

| Integration | Status | Package |
|---|---|---|
| JEI / REI / EMI | Auto-registered — every definition becomes a recipe-browser entry, no per-structure wiring | `compat.jei`/`rei`/`emi` |
| Patchouli | Manual: `PatchouliMultiblockHelper.register(definition)` during setup (shaped only; shapeless/procedural return `null`) | `compat.patchouli` |
| GuideME | Placeholder — no stable registration API yet; `GuideMEHelper.logInfo(...)` logs availability, register pages via GuideME's own JSON | `compat.guideme` |
| FTB Quests | Auto-registered (if loaded) — a "Multiblock" quest task ([below](#ftb-quests-compatibility)) | `compat.ftbquests` |

For recipe browsers, `.icon(itemId)` sets the icon; the title auto-derives from `multiblock.<namespace>.<path>` ([Visuals](api-reference/MultiblockBuilder.md#visuals-recipe-browsers)).

## FTB Quests compatibility

When FTB Quests is loaded, MultiLib registers a `multiblock` task type (reflection-gated, so no hard dependency). An author picks a definition and optionally a required [`MultiblockState`](api-reference/Multiblock-States-And-Progress.md); "Any" completes on formation alone.

Completion is **push-only**: the task listens to `MultiblockFormedEvent`/`MultiblockStateChangedEvent` and submits when a matching event fires. It deliberately doesn't consult the persistent "ever reached" record (which would let a quest re-complete on reset or credit a since-broken structure). A `requiredState` needs a real controller — JSON-only multiblocks never fire the state event, so use "Any" for those. Clicking the task opens the recipe viewer, not a manual complete.

## Progress tracking

[`MultiblockProgressAPI.compute(level, corePos)`](api-reference/Multiblock-States-And-Progress.md#multiblockprogressapi) reports how complete a not-yet-formed shaped structure is — total required, missing/mismatched positions, and a per-block "shopping list" — for building your own progress UI. Read-only; complements the [ghost overlay](#ghost-overlay).

## KubeJS scripting

Structures can be created and modified from KubeJS scripts. See [KubeJS Integration](KubeJS-Integration.md).

## See also

- [Core Concepts](Core-Concepts.md), [Pattern Design Guide](Pattern-Design-Guide.md), [Directional Cores Guide](Directional-Cores-Guide.md), [KubeJS Integration](KubeJS-Integration.md)
- [MultiblockBuilder](api-reference/MultiblockBuilder.md), [BlockDefinition](api-reference/BlockDefinition.md), [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md), [Multiblock States & Progress](api-reference/Multiblock-States-And-Progress.md)

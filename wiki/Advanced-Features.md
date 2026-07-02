[← Back to Home](Home.md)

# Advanced Features

Secondary systems beyond the basic shaped-pattern-plus-callbacks flow covered in [Core Concepts](Core-Concepts.md) and the [Pattern Design Guide](Pattern-Design-Guide.md). Every heading below is linked from elsewhere in the wiki — this page is the landing spot for those `#anchor` links.

## Shapeless structures

`.shapeless()` switches a definition from a fixed layer grid to a **flood-fill** match, handled by `ShapelessMatcher`. Instead of comparing a grid at a computed origin, the matcher flood-fills outward from the placed block (6-directional, stopping at air) up to `.maxSize(x, y, z)` (default `64,64,64` — this also bounds the flood-fill radius), then validates the resulting blob:

```java
MultiLibAPI.define(id)
        .shapeless()
        .minSize(3, 3, 3)
        .maxSize(9, 9, 9)
        .shell(BlockIngredient.of(Blocks.IRON_BLOCK))
        .interior(BlockIngredient.any())
        .require(BlockIngredient.of(Blocks.DIAMOND_BLOCK), 1, 4)
        .core('O') // still needs a core/activation symbol bound via .key(...) somewhere the flood-fill can reach
        .build();
```

Shapeless structures don't rotate — there's no fixed grid to rotate, so `MultiblockInstance.getTransform()` always reports `TransformData(0, false, "NONE")` for them.

### Shell / interior matching

See [Shell/interior matching](#shellinterior-matching) below — the same mechanism used here.

## Shell/interior matching

For shapeless structures, MultiLib classifies every flood-filled block as **shell** (touches the bounding box's boundary on at least one axis) or **interior** (doesn't):

- `.shell(ingredient)` — required ingredient for every shell block, unless a face-specific override applies.
- `.shellFace(Direction, ingredient)` — per-face override (e.g. a different top face than the four walls).
- `.interior(ingredient)` — required ingredient for every strictly-interior block.
- `.require(ingredient, min, max)` — a count constraint checked across the **whole** flood-filled region (shell + interior together), e.g. "between 1 and 4 diamond blocks somewhere inside."

All of these are shapeless-only — they have no effect on shaped (`.layer(...)`) definitions.

## Procedural patterns (`PatternProvider`)

Instead of `.layer(...)`, `.pattern(PatternProvider)` lets a structure's shape be computed rather than typed out:

```java
@FunctionalInterface
public interface PatternProvider {
    @Nullable BlockIngredient getIngredientAt(int x, int y, int z);
    default Vec3i getSize() { return new Vec3i(1, 1, 1); }
}
```

`getIngredientAt` returns `null` for "no block here" (equivalent to a space in a textual layer); `getSize()` gives the provider's own bounding box, which you can override with `.boundingBox(x, y, z)` on the builder. `FunctionalMatcher` searches procedural patterns with the exact same rotation/orientation machinery as `ShapedMatcher` (it calls `ShapedMatcher.applyTransform(...)` directly) — see the [Rotation & Matching Deep Dive](Rotation-And-Matching.md).

Built-in providers (`net.astronomy.multilib.api.pattern.providers`):

| Provider | Shape |
|---|---|
| `SphereProvider(radius, ingredient)` | Solid sphere |
| `HollowSphereProvider(radius, ingredient)` | Sphere shell only (a thin one-block-thick shell) |
| `CylinderProvider(radius, height, ingredient)` | Solid cylinder along Y |
| `HollowCubeProvider(width, height, depth, shell, interior)` | Cube with a required shell ingredient and an optional interior ingredient (`interior == null` means the inside is unconstrained) |
| `PyramidProvider(baseSize, ingredient)` | Stepped pyramid, `baseSize` layers tall, each layer's footprint shrinking by 2 per Y step |
| `LayeredPatternProvider(layers, blockMap)` | Wraps a textual layer grid as a `PatternProvider` — what `.layer(...)` uses internally; useful if you want to combine a hand-authored shape with `.boundingBox(...)` overrides |

Write your own by implementing `PatternProvider` directly for shapes not covered above (an ellipsoid, a procedurally-generated maze, etc.) — it's a single-method functional interface.

## JSON/datapack definitions

Structures can be defined entirely in datapacks under `data/<namespace>/multiblocks/<name>.json`, loaded by `MultiblockJsonLoader` (a `SimpleJsonResourceReloadListener`) and swapped cleanly on `/reload` via `MultiblockRegistry.registerJson`/`clearJsonDefinitions` — without touching any Java-registered definitions.

Recognized top-level fields (all optional except `layers`+`keys` or `pattern`):

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
  "name": "my_structure",
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

`keys` entries accept either a bare block id string (shorthand for `{"block": ...}`) or a full ingredient object:

| Ingredient JSON shape | Equivalent `BlockIngredient` |
|---|---|
| `"minecraft:stone"` or `{"block": "minecraft:stone"}` | `BlockIngredient.of(...)` |
| `{"block": "...", "properties": {...}}` | `BlockIngredient.ofState(...)` — ⚠️ property parsing is a stub today; a JSON key with `properties` set logs a warning and falls back to plain `of(...)` behavior (see `MultiblockCodecs.BLOCK_INGREDIENT_OBJECT`) |
| `{"tag": "examplemod:some_tag"}` | `BlockIngredient.tag(...)` |
| `{"any_of": [...ingredient objects...]}` | `BlockIngredient.anyOf(...)` |
| `{"any": true}` | `BlockIngredient.any()` |
| `{"type": "...", ...}` | A custom ingredient registered via `MultiblockSerializers.registerIngredient(...)` |

`pattern.type` supports the five built-in providers above (`multilib:sphere`, `multilib:cylinder`, `multilib:hollow_sphere`, `multilib:hollow_cube`, `multilib:pyramid`) plus any custom `PatternProviderSerializer` registered via `MultiblockSerializers.registerProvider(...)`.

Not currently expressible in JSON (Java-only): callbacks beyond the two built-in sound hooks (`onFormed`/`onBroken` with arbitrary logic), `.validator(...)`, `.onTick(...)`/`.onAmbient(...)`, `.freeBlock(...)`, `.optionalLayer(...)`, geometry constraints (`.unique(...)`/`.surfaceOnly(...)`/etc.), and `.model(...)`/`.keepVisible(...)` (Master-Dummy). Use a Java-registered definition instead if you need these.

## Master-Dummy model

`.model(ResourceLocation modelId)` + `.keepVisible(char... symbols)` gives a formed structure a single-block appearance: once formed, every part block becomes invisible (via a `MODEL_HIDDEN` blockstate property added by `AbstractMultiblockPartBlock`/`AbstractMultiblockControllerBlock`) except the core and any symbols listed in `.keepVisible(...)` — the core renders `modelId`'s default-state model in its place. Physics and hitboxes are unaffected; this is purely a render-time illusion.

Requirements:
- The core's `Block` must extend `AbstractMultiblockControllerBlock`; part blocks must extend `AbstractMultiblockPartBlock`.
- Hiding/unhiding is wired automatically through `AbstractMultiblockControllerBE.onStructureFormed`/`onStructureBroken` — you don't call `setModelHidden(...)` yourself in normal use.

See [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md) for the base classes.

## Wall sharing

Non-core/non-activation symbols can optionally "share" a block with an adjacent structure's matching symbol — two neighboring instances of the same (or different) structures reusing a shared wall rather than each requiring its own dedicated blocks — but this is **opt-in, not the default**. `MultiblockDefinition.getWallSharingMode(char symbol)` resolves this per symbol through a priority chain, highest first:

1. **Symbol-level override** — `.key(symbol, ingredient, WallSharingMode mode)` or `.noWallSharing(symbols...)` on the builder.
2. **Block-level registration** — a `BlockIngredient`/block implementing `IWallSharable.getDefaultWallSharingMode()`, or a `BlockDefinition.getWallSharingOverride()` set via `MultiLibAPI.block(block).wallSharing(enabled)`.
3. **Definition-level default** — `.wallSharing(boolean)` on the builder, itself `false` unless set.
4. **Fallback** — for ordinary symbols, `ENABLED` only if the definition-level default from step 3 was set to `true`; otherwise `DISABLED`. The core/activation symbol always falls back to **`DISABLED`** regardless of the definition-level default, unless explicitly overridden by one of the steps above (a structure's "main" block never shares a wall unless you say so explicitly).

`WallSharingMode` has three values: `ENABLED`, `DISABLED`, `INHERIT` (defers to the next link in the chain).

## Auto-place

`.autoPlace()` opts a definition into Ctrl+Right-click auto-placement: clicking the (unformed) core with the modifier held scans every fixed-grid pattern position (free-block positions are skipped), and for each missing (air) cell whose expected block the player is holding, places it — consuming one item per placement, or placing for free in creative. After placing, it immediately attempts formation (`BlockActivationHandler.triggerFormationAt`) so a fully-stocked player can complete a structure in one click. The player is notified via an action-bar message of how many blocks were placed and how many are still missing (for lack of matching items in inventory).

## Ghost overlay

Independent of `.autoPlace()`, right-clicking (a configured key/interaction on) the **core** of an unformed structure shows a client-side ghost overlay: translucent renders at every still-missing or mismatched position, refreshed live every 10 ticks while open, auto-expiring after a configurable duration (`CommonConfig`). Clicking a horizontal face of the core cycles which yaw orientation (0°/90°/180°/270°) is previewed; clicking again cycles through showing one layer at a time vs. the whole structure. `.ghostOverlayDebug()` on the builder adds a chat line reporting the overlay's render time each frame — a dev tool, not meant to ship enabled.

If the core's `BlockDefinition` has `.mainFace()` set (see the [Directional Cores Guide](Directional-Cores-Guide.md)), the preview orientation is pinned to the core's actual placed facing instead of the player's look direction or clicked face.

## IO ports

`MultiLibAPI.block(block).ioPort().build()` marks a block as an IO port: item/fluid/energy `BlockCapability` requests on it are transparently forwarded to the controller block entity of whichever multiblock instance currently contains it (`IOPortCapabilityHandler`, registered against NeoForge's `RegisterCapabilitiesEvent`). This lets you place a small, visually distinct "input/output" block anywhere in a large structure and have it act as a proxy for the actual controller's inventory/energy/fluid handlers, without writing the capability-forwarding logic yourself.

## Wrench tool

A "wrench" is any `Item` implementing the marker interface `IMultiblockWrench` (single method: `useOn(UseOnContext)`, since it extends the normal item-use contract). **MultiLib ships no wrench item of its own** — implement the interface on your own tool. `ExampleWrenchItem` (`net.astronomy.multilib.example`) is a full reference implementation covering:

- Reporting "not part of any registered multiblock" when the clicked block matches nothing.
- Reporting the formed instance's current `MultiblockState` id if the structure is already formed.
- Attempting formation (respecting `FormationMode.allowsWrench()`) if not yet formed, and reporting the `MatchFailureReport` summary if the attempt still fails.

Only structures whose `FormationMode` allows wrench-triggering (`WRENCH` or `AUTOMATIC_AND_WRENCH`) actually form this way — see [Core Concepts § Formation modes](Core-Concepts.md#formation-modes).

## JEI / REI / EMI / Patchouli / GuideME / FTB Quests compatibility

| Integration | Status | Package |
|---|---|---|
| JEI | Auto-registered via `@JeiPlugin` — every registered `MultiblockDefinition` becomes a recipe-browser entry automatically, no per-structure wiring needed | `compat.jei` |
| REI | Equivalent auto-registered plugin | `compat.rei` |
| EMI | Equivalent auto-registered plugin | `compat.emi` |
| Patchouli | Manual: call `PatchouliMultiblockHelper.register(definition)` yourself during common setup — converts a shaped `MultiblockDefinition` into Patchouli's `IMultiblock` format (only shaped definitions are supported; shapeless/`PatternProvider`-backed definitions return `null` and are skipped) | `compat.patchouli` |
| GuideME | Placeholder only — GuideME doesn't expose a stable programmatic registration API yet. `GuideMEHelper.logInfo(definition)` just logs availability; register your GuideME pages via GuideME's own datapack JSON format, referencing the definition's `ResourceLocation` | `compat.guideme` |
| FTB Quests | Auto-registered (if FTB Quests is loaded) — adds a "Multiblock" quest task type. See [FTB Quests compatibility](#ftb-quests-compatibility) below | `compat.ftbquests` |

For JEI/REI/EMI, `.icon(itemId)` and `.name(...)` control the recipe-browser presentation (icon item and display name) — see [MultiblockBuilder § Visuals & recipe browsers](api-reference/MultiblockBuilder.md#visuals-recipe-browsers).

## FTB Quests compatibility

If FTB Quests is loaded, MultiLib registers a `multiblock` quest task type (`FtbQuestsCompat`, gated behind reflection so the base mod never hard-depends on FTB Quests classes). A quest author picks a registered `MultiblockDefinition` and, optionally, a required [`MultiblockState`](api-reference/Multiblock-States-And-Progress.md) from a searchable dropdown in the task's config UI; leaving the state as "Any" completes the task on formation alone.

Completion is **push-only, never polled**:

- The task subscribes to `MultiblockFormedEvent` and `MultiblockStateChangedEvent` (via `MultiblockQuestEventListener`) and submits itself the instant a matching event fires for the right player, definition, and (if set) state.
- It does **not** use [`MultiblockProgressionTracker`](api-reference/Multiblock-States-And-Progress.md#multiblockprogressiontracker)'s persistent "ever reached" record for its completion check — a permanently-standing historical record would let a quest re-complete instantly on reset, or "complete" a task for a structure that's since been broken. The live-event approach avoids both.
- A task with a `requiredState` set can only ever be satisfied by a multiblock that has a real `AbstractMultiblockControllerBE` controller (JSON-only multiblocks with no controller never fire `MultiblockStateChangedEvent`, so pick "Any" for those).

Clicking the task in the quest screen opens the recipe viewer (JEI/REI/EMI, whichever is installed) on that structure instead of manually completing it — there's no "click to complete" behavior for this task type.

## Progress tracking for in-progress structures

[`MultiblockProgressAPI.compute(level, corePos)`](api-reference/Multiblock-States-And-Progress.md#multiblockprogressapi) reports how complete a **not-yet-formed** shaped (`.layer(...)`) structure is — total blocks required, which positions are still missing/mismatched, and a per-block-type "shopping list" — so you can build your own progress UI without reimplementing pattern matching. It's read-only and complements, rather than replaces, the [ghost overlay](#ghost-overlay) above.

## See also

- [Core Concepts](Core-Concepts.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Directional Cores Guide](Directional-Cores-Guide.md)
- [MultiblockBuilder reference](api-reference/MultiblockBuilder.md)
- [BlockDefinition reference](api-reference/BlockDefinition.md)
- [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md)
- [Multiblock States & Progress Tracking](api-reference/Multiblock-States-And-Progress.md)

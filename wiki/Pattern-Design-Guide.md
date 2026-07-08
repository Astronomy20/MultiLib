[← Back to Home](index.md)

# Pattern Design Guide

Design decisions for laying out a shaped pattern. Read [Core Concepts](Core-Concepts.md) first for the primitives (symbols, `BlockIngredient`, layers, core/activation); this page is about using them well.

## Sketch it top-down first

Draw one grid per Y level, **top level first** — the first `.layer(...)` call is the top, the last is the bottom (reverse of the old `PatternBuilder`). Within a level, rows go lowest-Z to highest-Z, characters go lowest-X to highest-X. Reading a `.layer(...)` block top-to-bottom as printed matches drawing the structure from the front:

```java
.layer("PPP",   // top    (relY = 0)
       " P ",
       " G ")
.layer("POP",   // bottom (relY = -1), 'O' is the core
       " P ",
       " G ")
```

## Choose core and activation deliberately

- One obvious controller? Make it the **core** with `.core(char)`. This also sets **activation** to the same symbol, so placing the controller last both triggers the check and anchors the ghost overlay, wrench, and controller state.
- Split them only when the trigger block genuinely isn't the block that represents the structure (e.g. trigger on any body block, track state on a separate controller).
- The core needn't be placed last: `setValidationInterval(...)` on a controller lets a complete structure be discovered periodically ([activation flow](Core-Concepts.md#activation-flow)).

## Ingredients per symbol

Use the narrowest ingredient that expresses your intent. A tag/predicate/`any()` on the **activation or core** symbol makes the whole definition always-checked against every placement in the world ([performance note](api-reference/BlockIngredient.md#performance-note)) — reserve those for body symbols:

```java
.key('B', BlockIngredient.tag(BlockTags.LOGS))                // body — cheap
.key('O', BlockIngredient.of(ExampleSetup.CONTROLLER_BLOCK))  // core — enumerable, indexed
```

Use `.ofState(...)` when a body block needs a specific facing or property, not just the right block.

## Empty cells

Space (`' '`) means "don't care" — use it for cells that shouldn't constrain matching. A stray character never bound with `.key(...)` is treated the same, so check layer strings against your symbol map; typos aren't caught.

To require **air** specifically, use `.requireAirInEmptyPositions()` — but it only affects `PatternProvider`/functional definitions; shaped layers never constrain spaces.

## Optional cells and layers

- `.optional(char... symbols)` — lets a symbol mismatch without failing the match. Good for decorative variance.
- `.optionalLayer(String... rows)` — a layer tried both with and without (e.g. a taller variant). The matcher tries every include/exclude combination, so keep the count small — it's combinatorial.

## Free blocks

`.freeBlock(char, ingredient, min, max)` declares a symbol not pinned to a fixed cell: MultiLib scans the bounding box for unclaimed cells matching `ingredient` and counts them toward `min`/`max` — e.g. "2–4 lanterns anywhere in the frame." Restrict candidates with the `List<BlockPos> allowedPositions` overload.

## Geometry constraints

Validated against your layers at `.build()`:

| Constraint | Requires the symbol to be |
|---|---|
| `.unique(char...)` | Present exactly once (for `freeBlock` symbols, forces `min=max=1`) |
| `.surfaceOnly(char...)` | On an outer face (≥1 boundary axis) |
| `.frameOnly(char...)` | On an edge/corner (≥2 boundary axes) |
| `.insideOnly(char...)` | Touching no boundary |

A violation doesn't throw but blocks registration — `.build()` logs the error, skips `MultiblockRegistry.register(...)`, and returns the unregistered object. Check your logs after adding these.

## Rotation

Decide early: any horizontal facing (`RotationMode.HORIZONTAL`, default), fully free including tipped (`RotationMode.ALL`), or fixed (`RotationMode.NONE`). For per-axis control (e.g. 180° but not 90°), use `.allowRotation(...)` — see [Rotation & Matching](Rotation-And-Matching.md).

If the core has its own facing (a player-placed furnace-like block), pin the preview to it via `.mainFace()` — see the [Directional Cores Guide](Directional-Cores-Guide.md). This is independent of whether the matcher allows rotation.

## Wall sharing

**Disabled by default** — non-core symbols don't share a block with an adjacent structure unless you opt in. `.wallSharing(true)` enables it definition-wide; `.noWallSharing(char...)` re-excludes specific symbols. Full priority chain: [Advanced Features § Wall sharing](Advanced-Features.md#wall-sharing).

## Visual polish

`.icon(...)` and `.name(...)` for recipe browsers; `.model(...)` + `.keepVisible(...)` for a [Master-Dummy](Advanced-Features.md#master-dummy-model) single-block look; `.ghostOverlayDebug()` while iterating (remove before shipping).

## See also

- [Core Concepts](Core-Concepts.md), [Rotation & Matching](Rotation-And-Matching.md), [Directional Cores Guide](Directional-Cores-Guide.md), [Advanced Features](Advanced-Features.md)
- [MultiblockBuilder reference](api-reference/MultiblockBuilder.md)

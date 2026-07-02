[← Back to Home](index.md)

# Pattern Design Guide

Practical guidance for laying out a shaped pattern with `MultiblockBuilder`. Read [Core Concepts](Core-Concepts.md) first for the underlying model (symbols, `BlockIngredient`, layers, core/activation) - this page is about *design decisions* once you know the primitives.

## Sketch on paper (or in a comment) first

Before writing any `.layer(...)` calls, draw your structure top-down, one grid per Y level, **top level first** (⚠️ remember: the *first* `.layer(...)` call is the top of the structure, the *last* is the bottom - the reverse of the old `PatternBuilder` API). For each level, rows go from lowest Z (first string) to highest Z (last string), and characters go from lowest X (leftmost) to highest X (rightmost).

`ExamplePattern` in the source tree lays out a small altar-like structure this way:

```java
.layer("PPP",   // top layer (relY = 0)
       " P ",
       " G ")
.layer("POP",   // bottom layer (relY = -1) - 'O' is the core
       " P ",
       " G ")
```

Reading this top-to-bottom as printed *is* reading it top level first, row-by-row within each level - which conveniently matches how you'd naturally draw the structure looking at it from the front with the top level above the bottom level on the page.

## Pick your core and activation symbols deliberately

- If your structure has one obvious controller block (a block entity with a menu, a "master" block), make it the **core** with `.core(char)`. This also sets **activation** to the same symbol unless you call `.activation(char)` separately - so in the common case, placing the controller last both triggers the check and gives you a stable anchor for the ghost overlay, wrench diagnostics, and (if used) `AbstractMultiblockControllerBE` state.
- Split core and activation only when the "block whose placement should trigger a check" genuinely isn't the "block that represents the structure." For example, if you want the structure to try forming whenever *any* body block is placed, but still track state on a separate controller elsewhere in the pattern.
- The core doesn't have to be placed last in practice - `setValidationInterval(...)` on an `AbstractMultiblockControllerBE` lets an already-complete structure be discovered periodically even if the core was placed first (see [Core Concepts § Activation flow](Core-Concepts.md#activation-flow)).

## Choosing ingredients per symbol

Use the narrowest `BlockIngredient` that still expresses your intent - see the [performance note in the `BlockIngredient` reference](api-reference/BlockIngredient.md#performance-note): a tag/predicate/`any()` used on your **activation or core** symbol makes the whole definition "always-checked" against every block placement in the world, not just placements of specific blocks. Reserve those for body symbols where the cost doesn't apply:

```java
.key('B', BlockIngredient.tag(BlockTags.LOGS))       // body - fine, cheap to check
.key('O', BlockIngredient.of(ExampleSetup.CONTROLLER_BLOCK)) // core/activation - enumerable, indexed
```

Use `BlockIngredient.ofState(...)` when a body block needs a specific facing or property, not just the right `Block` - e.g. matching a furnace that must face a specific way relative to the pattern.

## Empty cells

The space character (`' '`) means "don't care" and is never treated as a symbol - use it freely for cells that shouldn't constrain matching (like the corners of a 3×3 layer that aren't actually part of the structure). Any stray character in a layer string that was never bound with `.key(...)` is silently treated the same as a space, so double-check your layer strings against your symbol map - there's no validation that catches a typo here.

If you actually need a cell to require **air** specifically (not "don't care"), that's what `.requireAirInEmptyPositions()` is for - but note it only takes effect for `PatternProvider`-backed/functional definitions; plain shaped layers already only check non-space symbols and won't enforce air on spaces even with this flag set.

## Optional cells and layers

- `.optional(char... symbols)` - lets specific symbols mismatch without failing the whole match. Good for decorative variance (e.g. a symbol that's "usually gold but the structure still works without it").
- `.optionalLayer(String... rows)` - an entire layer the matcher tries both with and without. Useful for structures with an optional "extra tier" (e.g. a 2-block-tall or 3-block-tall variant of the same base). The matcher tries every combination of included/excluded optional layers, so keep the number of optional layers small - it's combinatorial.

## Free blocks

`.freeBlock(char symbol, BlockIngredient ingredient, int min, int max)` declares a symbol that isn't pinned to a fixed layer position at all: MultiLib scans the pattern's bounding box for any unclaimed cell matching `ingredient` and counts matches toward `min`/`max`. Use this for "decorate with N of these somewhere in the structure" requirements rather than a fixed grid position - e.g. "between 2 and 4 lanterns anywhere in the frame." Restrict to specific candidate cells with the `List<BlockPos> allowedPositions` overload if "anywhere" is too permissive.

## Geometry constraints

Four constraints are statically validated against your textual layers at `.build()` time (not re-checked per-match at runtime beyond the shape matching itself):

| Constraint | Requires |
|---|---|
| `.unique(char... symbols)` | Symbol occurs exactly once in the whole structure (for `freeBlock` symbols, forces `min=max=1` instead) |
| `.surfaceOnly(char... symbols)` | On at least one boundary axis (an outer face) |
| `.frameOnly(char... symbols)` | On at least two boundary axes (an edge/corner) |
| `.insideOnly(char... symbols)` | Touches no boundary at all |

Violations are logged as build-time errors - worth checking your logs after adding these, since a violated constraint doesn't throw, but it does block registration: `.build()` skips calling `MultiblockRegistry.register(...)` and still returns the (unregistered) definition object.

## Rotation

Decide early whether your structure is meant to be built facing any horizontal direction (the common case - `RotationMode.HORIZONTAL`, the default), fully free (`RotationMode.ALL`, including tipped/upside-down), or fixed (`RotationMode.NONE`). If you need asymmetric per-axis control (e.g. allow 180° tipping but not 90°), use `.allowRotation(...)` instead - see the [Rotation & Matching Deep Dive](Rotation-And-Matching.md) for how these interact with the matcher's search.

If the structure's core has its own meaningful facing (a furnace-like block placed by the player), see the [Directional Cores Guide](Directional-Cores-Guide.md) for pinning the ghost overlay/auto-place preview to that facing via `.mainFace()` - this is independent from whether the *matcher* allows rotation.

## Wall sharing

Wall sharing is **disabled by default** - non-core/non-activation symbols do *not* share a block with an adjacent structure's matching symbol unless you opt in. Call `.wallSharing(true)` to enable it definition-wide, then use `.noWallSharing(char...)` (or per-symbol overrides / an `IWallSharable` block) if specific symbols still need their own dedicated blocks. See [Advanced Features § Wall sharing](Advanced-Features.md#wall-sharing) for the full priority chain.

## Visual polish

Once the shape is solid: `.icon(...)` and `.name(...)` for recipe-browser presentation, `.model(...)` + `.keepVisible(...)` if you want a Master-Dummy single-block appearance once formed (see [Advanced Features § Master-Dummy model](Advanced-Features.md#master-dummy-model)), and `.ghostOverlayDebug()` temporarily while iterating on layout (remove before shipping).

## See also

- [Core Concepts](Core-Concepts.md)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [Directional Cores Guide](Directional-Cores-Guide.md)
- [Advanced Features](Advanced-Features.md)
- [MultiblockBuilder reference](api-reference/MultiblockBuilder.md)

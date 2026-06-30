# Core Concepts

This page explains the model MultiLib uses to describe and detect a multiblock structure. Read this before designing your own patterns — the coordinate system and registration rules below are not optional details, they're the contract the matcher relies on.

## What is a "pattern" in MultiLib?

A **pattern** (`PatternManager`) is an immutable description of:

- a set of **keys** — single characters mapped to specific `Block`s,
- one or more **layers** — horizontal slices of the structure described with those key characters,
- a set of **rotation flags** controlling which orientations of the structure should be recognized,
- an optional **action** — code to run when the structure is detected in the world.

You never construct a `PatternManager` directly; you assemble it with `PatternBuilder` (via `PatternManager.pattern()` or `MultiLibAPI.pattern()`) and finish with `.build()`.

## Keys

A key is a single `char` bound to one `Block` via `.key(char, Block)`. Keys are **global to the whole pattern**, not per-layer — the same character means the same block on every layer you add. There is no per-layer key remapping.

The space character (`' '`) is reserved and always means **"don't care / no constraint here"** — it is never treated as a key, even if you never call `.key(' ', ...)`. Use it for empty cells inside a layer's bounding box (air, or any block — the matcher simply skips that cell).

Any character that appears in a layer string but was never registered with `.key(...)` is silently ignored as if it were a space (see [`PatternMatcher`](api-reference/PatternMatcher.md#caveats)) — there is currently no validation that catches typos in layer strings against your key map. Double-check your key/layer characters match exactly.

## Layers and the coordinate system

A layer is added with `.layer(String... rows)`. Each call to `.layer(...)` adds **one horizontal (Y) slice** of the structure:

- **Row order within a layer → Z axis.** The first string you pass is the row at the lowest Z offset (`relZ` more negative), each subsequent string increases Z by 1.
- **Character order within a row → X axis.** The leftmost character is the lowest X offset, increasing left-to-right.
- **Order of `.layer(...)` calls → Y axis, bottom to top.** The **first** `.layer(...)` call is the **bottom** of the structure. The **last** `.layer(...)` call is the **top**, and also the layer used as the Y reference (offset 0) when computing where to search for a match.

```
.layer(" D ",   // row 0 → lowest Z
        "EDO",   // row 1
        " D ")   // row 2 → highest Z
```

reading this single layer: `E` is west (-X) of the center, `O` is east (+X) of the center, the two `D`s are north/south (-Z/+Z) of the center, all on the same Y level.

For a multi-layer structure:

```java
PatternManager.pattern()
        .key('B', Blocks.STONE_BRICKS)
        .key('G', Blocks.GOLD_BLOCK)
        .layer("BBB",   // bottom (first call)
               "BBB",
               "BBB")
        .layer(" G ",   // top (last call)
               " G ",
               " G ")
        .build();
```

Each layer is centered independently: the **center column** of a layer is `row.length() / 2` (integer division) and the **center row** is `layer.size() / 2`. This means:

- All rows within one `.layer(...)` call **must have equal length** — the matcher derives the layer's width from the *first* row only (`layer.getFirst().length()`); rows of different lengths in the same layer produce undefined/incorrect matching, not an error.
- Different layers **can** have different widths/heights — each is centered on its own geometric middle independently, so layers don't need to align unless you deliberately design the offsets to line up.
- With odd dimensions the center cell sits exactly on a character; with even dimensions the center falls between two characters (integer division rounds down), which shifts your structure's effective center by half a block. Prefer odd width/height per layer unless you've deliberately accounted for this.

## Registration and lookup

`.build()` does two things:

1. Constructs the immutable `PatternManager`.
2. If you called `.action(...)` before `.build()`, registers the pattern + action pair into `PatternRegistry`. **If you never set an action, the pattern is built but not registered** — it will never be matched against the world, since the placement listener only looks up patterns through the registry.

`PatternRegistry` indexes nothing more than "give me every pattern that uses block X as one of its keys" (`getPatternsFor(Block)`). This is what the placement event handler uses to avoid scanning every registered pattern on every block placement in the world — only patterns relevant to the placed block are checked.

## Activation flow

1. A block is placed in the world (`BlockEvent.EntityPlaceEvent`, server side only).
2. MultiLib looks up every registered pattern that uses the placed block as a key (`PatternRegistry.getPatternsFor`).
3. For each candidate pattern, `PatternMatcher.matches(...)` searches every position where the *placed* block could plausibly be one of the pattern's matching cells, and tries every rotation/orientation the pattern allows (see the [matching deep dive](api-reference/PatternMatcher.md) for the full algorithm).
4. The **first** pattern that matches wins — its `PatternAction.onMatch(level, origin, transform)` is invoked once, and no further candidate patterns are checked for that placement.
5. Nothing is consumed or removed automatically. If your action represents "the structure is consumed/transforms into something else," your `PatternAction` is responsible for removing the blocks itself — see `PatternAction.clearStructure(...)`.

There is **no continuous/periodic check** — patterns are only evaluated at the moment a relevant block is placed. Breaking a block that was part of an already-matched structure does not currently trigger any callback; MultiLib does not track "formed" multiblocks as persistent objects (see [Known Limitations](#known-limitations)).

## Rotation flags

`PatternBuilder` exposes four boolean flags:

| Flag | Default | Meaning |
|---|---|---|
| `allowHorizontalRotation` | `true` | Intended to control whether the structure should match when rotated around the Y axis. **Currently has no effect** — see Known Limitations. |
| `allowVerticalRotation` | `false` | Enables matching the structure tipped onto its side (rotated around the X or Z axis), in addition to its upright orientation. |
| `allowSideRotation` | `false` | Refines vertical-rotation search to include the additional side-facing orientations. Requires `allowVerticalRotation = true`. |
| `allowUpsideDown` | `false` | Refines vertical-rotation search to include the upside-down orientation. Requires `allowVerticalRotation = true`. |

`PatternBuilder.build()` throws `IllegalStateException` if `allowSideRotation` or `allowUpsideDown` is `true` while `allowVerticalRotation` is `false` — set vertical rotation first.

For exactly how rotation search is performed, see the [PatternMatcher reference](api-reference/PatternMatcher.md) and the rotation/matching deep dive *(planned)*.

## Known limitations

These are current, real limitations of the implementation — documented here so you don't design around behavior that doesn't exist:

- **`allowHorizontalRotation` is not enforced.** `PatternMatcher` always tries all four Y-axis rotations regardless of this flag's value. In practice, every pattern today matches in any horizontal orientation. If your structure has a "front" that must face a specific direction, you currently have to enforce that yourself inside your `PatternAction` by inspecting `TransformData.rotation()` and aborting/ignoring the match when it isn't the orientation you expect.
- **No formed-state tracking.** MultiLib does not keep a registry of "currently formed" multiblocks, doesn't fire a "structure broken" callback, and doesn't validate that the structure still exists before re-triggering. Each match is a one-shot reaction to a block placement.
- **No key-character validation against layer strings.** A typo in a layer row that doesn't correspond to any registered key is silently treated as empty space.
- **`MultiBlockPattern` and `SummonPattern`** (under `pattern.type`) are currently empty placeholder classes — only `ExamplePattern` is a working reference example in the source tree.

## See also

- [Getting Started](Getting-Started.md) — minimal working example
- [PatternBuilder](api-reference/PatternBuilder.md), [PatternManager](api-reference/PatternManager.md), [PatternMatcher](api-reference/PatternMatcher.md), [PatternRegistry](api-reference/PatternRegistry.md), [PatternAction](api-reference/PatternAction.md)

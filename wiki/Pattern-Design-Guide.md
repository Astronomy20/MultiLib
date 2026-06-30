[← Back to Home](Home.md)

# Pattern Design Guide

A practical, how-to guide for laying out patterns correctly the first time. Assumes you've read [Core Concepts](Core-Concepts.md).

## 1. Sketch in three 2D grids before writing code

Draw your structure as one grid per Y level (bottom to top), each cell either a key letter or blank. This maps directly onto `.layer(...)` calls:

```
Y=2 (top):     Y=1 (middle):    Y=0 (bottom):
 . G .          . . .            B B B
 . G .          . W .            B B B
 . G .          . . .            B B B
```

becomes:

```java
PatternManager.pattern()
        .key('B', Blocks.STONE_BRICKS)
        .key('W', Blocks.WATER) // careful: see "fluids and non-solid blocks" below
        .key('G', Blocks.GOLD_BLOCK)
        .layer("BBB", "BBB", "BBB")   // Y=0, bottom — first call
        .layer(" . ", ".W.", " . ")   // wrong: '.' is not a registered key!
        .layer(" G ", " G ", " G ")   // Y=2, top — last call
        .build();
```

> The example above has a deliberate bug: `.` was used for "empty" instead of `' '` (space). MultiLib treats *any* unrecognized character as "no constraint," so `.` technically "works" the same as space today — but don't rely on that. Always use `' '` for empty cells; using arbitrary placeholder characters that happen to not be registered keys is fragile and will silently break if you ever reuse that character as a real key elsewhere.

## 2. Prefer odd dimensions

Per [Core Concepts](Core-Concepts.md#layers-and-the-coordinate-system), the center of a layer is `length / 2` (integer division). With even width/height, the "center" falls between two cells, silently shifting your structure's effective center by half a block relative to what you might expect. Unless you've deliberately designed around this, keep each layer's width and height **odd** (1, 3, 5, ...).

## 3. Keep every row in a layer the same length

The matcher derives a layer's width from its **first row only**. If later rows in the same `.layer(...)` call are shorter or longer, there's no validation — you'll get incorrect matching with no error message. Pad short rows with trailing spaces to match the longest row's length.

```java
// BAD — second row is missing a trailing space, widths don't match (3 vs 2)
.layer("ABC", "AB")

// GOOD
.layer("ABC", "AB ")
```

## 4. Decide your reference point deliberately

`origin` passed to your `PatternAction` is always the **top layer's center cell**, in the matched orientation — not the bottom, not the literal block that was placed. If your structure logically wants its "core" block (a controller, an altar focus, etc.) to be where effects spawn, **put that key on the top layer, centered**, or compute the offset yourself inside your action.

## 5. Fluids and non-solid blocks as keys

`PatternMatcher` compares via `state.is(expectedBlock)` — this works for fluid blocks too (`Blocks.WATER`, `Blocks.LAVA`), since fluids have a corresponding `Block` in NeoForge/vanilla. Be aware that:

- Source vs. flowing fluid blocks are different `Block`s in some contexts — make sure you key on the one you actually expect in the finished structure.
- There's no property-level matching (see [PatternMatcher caveats](api-reference/PatternMatcher.md#caveats)), so you can't distinguish e.g. waterlogged vs. non-waterlogged via the key system alone.

## 6. Designing for rotation

- If your structure is meant to be buildable facing any horizontal direction and is **not** rotationally symmetric, you don't need to do anything special — all 4 horizontal rotations are always tried regardless of flags (see [Rotation & Matching Deep Dive](Rotation-And-Matching.md)). Just design the shape once.
- If your structure **must** have a fixed facing (a "front"), you need the manual `transform.rotation() != 0` rejection workaround described in [Rotation & Matching Deep Dive § What each rotation flag actually does](Rotation-And-Matching.md#what-each-rotation-flag-actually-does-today) — the builder flags alone cannot enforce this today.
- Only enable `allowVerticalRotation` if your structure genuinely makes sense tipped onto its side (e.g. a symmetric machine core). It roughly triples matching cost and triples the number of orientations a player can accidentally trigger your structure from — make sure that's actually desirable for your design.

## 7. Avoid key collisions across unrelated patterns

Since [`PatternRegistry.getPatternsFor(Block)`](api-reference/PatternRegistry.md#getpatternsforblock-block) is checked for *every* registered pattern using that block, and only the **first matching pattern wins** at a given placement (see [Core Concepts § Activation flow](Core-Concepts.md#activation-flow)), two patterns that share a common, generic key block (e.g. both use `Blocks.STONE`) and could plausibly both match the same structure will have a registration-order-dependent winner. If you're designing a pattern meant to coexist with others (e.g. building a library of structures for your own mod), prefer distinctive, less common key blocks for the cells the matcher reaches first, or make sure overlapping structures are intentionally distinguishable.

## 8. Test incrementally

Build your structure in-game one block at a time, watching for the action to fire only once the *true* last block goes in. If it fires early, your pattern likely has fewer required cells than you intended (e.g. a key character you meant to use isn't registered with `.key(...)`, silently turning it into "no constraint").

## Checklist before shipping a pattern

- [ ] Every character used in `.layer(...)` rows is either `' '` or a registered `.key(...)`.
- [ ] All rows within each `.layer(...)` call have equal length.
- [ ] Layer dimensions are odd, or the even-dimension center shift is intentional and accounted for.
- [ ] `.action(...)` is set before `.build()` (otherwise the pattern is never registered).
- [ ] If the structure needs a fixed facing, the action rejects non-zero `transform.rotation()` (or you've otherwise confirmed rotation-invariance is acceptable).
- [ ] Tested by physically building the structure and confirming the action fires exactly when expected.

## See also

- [Core Concepts](Core-Concepts.md)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [PatternBuilder reference](api-reference/PatternBuilder.md)

[← Back to Home](Home.md)

# Rotation & Matching Deep Dive

This page explains *why* matching behaves the way it does, building on the method-level facts in the [`PatternMatcher` reference](api-reference/PatternMatcher.md). Read [Core Concepts](Core-Concepts.md) first if you haven't.

## The mental model: search, don't compute

MultiLib does not compute "where could this pattern be" analytically. It **searches**: for the placed block, it tries every key cell of the pattern as a hypothesis ("what if *this* placed block is the diamond block at relative position (1, 0, -1)?"), derives what the pattern's origin would have to be under that hypothesis, and then brute-force compares the whole pattern against the world at that origin, across every rotation the pattern allows.

This has two practical consequences:

1. **You don't need to place a specific "trigger" block.** Any block belonging to the pattern, placed as the structure's last missing piece, can trigger a successful match — the search isn't anchored to one designated cell.
2. **Cost scales with pattern size and rotation flags**, not with world size. A 3×3×3 pattern with no rotation flags does at most `27 patterns of geometry × 4 rotations = 108` candidate comparisons, each `O(27)` block reads — cheap. The same pattern with `allowVerticalRotation(true)` triples the candidate origins per cell and adds two more rotation passes, multiplying the cost roughly 3×.

## Coordinate space recap

Pattern-local relative coordinates are computed once per cell as:

```
relX = col - centerX     centerX = layer.getFirst().length() / 2
relY = layerIndex - (layerCount - 1)
relZ = row - centerZ     centerZ = layer.size() / 2
```

`relY` is always `≤ 0`: the **top layer** (last `.layer(...)` call) is the `relY = 0` reference plane, and every layer below it has an increasingly negative `relY`. This is why `origin` in a `MatchResult`/`PatternAction` callback always corresponds to the **top** layer's center — not the bottom, and not wherever the player happened to stand.

## What each rotation flag actually does (today)

| Flag | Effect on search |
|---|---|
| `allowHorizontalRotation` | **None.** All 4 Y-axis rotations (0°/90°/180°/270°) are always tried, for every pattern, unconditionally. |
| `allowVerticalRotation` | Adds two extra families of candidate origins per matched cell (structure tipped around X, structure tipped around Z), each tried across all 4 horizontal rotations on top. |
| `allowSideRotation` / `allowUpsideDown` | Stored on the `PatternManager` and validated at build time (require `allowVerticalRotation`), but **not read anywhere in `PatternMatcher`** at present — they don't currently narrow or expand the vertical search beyond what `allowVerticalRotation` alone already does. |

**Practical implication:** as of this codebase, you cannot restrict a pattern to a single fixed horizontal facing using the builder flags alone — `allowHorizontalRotation(false)` will not stop the pattern from matching when rotated. If your structure needs a fixed orientation (e.g. an altar whose "front" must face a specific direction relative to some other context), you must enforce that yourself:

```java
.action((level, origin, transform) -> {
    if (transform.rotation() != 0) {
        return; // reject any non-canonical horizontal orientation
    }
    // ... proceed with formation logic ...
})
```

This is a manual workaround, not a builder feature — keep it in mind when designing structures that are supposed to have directional meaning (entrances, "front faces", etc.).

## Vertical rotation: what "tipped over" means geometrically

When `allowVerticalRotation` is enabled, the matcher doesn't just rotate the *search origin* — for each candidate cell, it also reinterprets which world axis plays the role of the pattern's Y axis:

- **Axis `X`:** the pattern's Y and Z axes are swapped before applying rotation — useful for structures that should also be recognized lying on their side along the X direction.
- **Axis `Z`:** the pattern's Y and X axes are swapped — same idea, lying along Z.

Combined with the 4 horizontal rotations tried per axis, a pattern with `allowVerticalRotation(true)` is checked in up to **12 orientations total** (4 upright + 4 tipped-on-X + 4 tipped-on-Z) at up to 3 different candidate origins per matched cell.

`matchesTransformed` performs the actual comparison: it derives each key cell's pattern-local offset, applies the vertical swap (duplicated logic, not delegated to `RotationUtils.rotateVertical` — see [`PatternMatcher` caveats](api-reference/PatternMatcher.md#caveats)), then the horizontal rotation, then reads the resulting world position.

## Worked trace (conceptual)

Given a 1-layer plus-shape pattern:

```java
.key('D', Blocks.DIAMOND_BLOCK)
.layer(" D ",
       "DDD",
       " D ")
```

Placing the *center* diamond block last: the matcher, scanning this layer, reaches the cell at `row=1, col=1` (`relX=0, relZ=0`), computes a candidate origin equal to `placedPos`, and `matchesTransformed` at `rotation=0` succeeds immediately since the plus shape is rotationally symmetric — no further rotations are even attempted.

Placing one of the *arm* diamond blocks last instead: the matcher reaches that cell with a non-zero relative offset, computes a candidate origin offset from `placedPos` accordingly, and the comparison still succeeds — because the shape is symmetric under all 4 rotations, `rotation=0` matches regardless of which arm was placed last.

For an **asymmetric** pattern, which specific rotation succeeds (and thus what `TransformData.rotation()` your action receives) genuinely depends on the real-world orientation of the built structure — this is the value to inspect if your action needs to know "which way is this thing facing."

## See also

- [Core Concepts](Core-Concepts.md)
- [PatternMatcher reference](api-reference/PatternMatcher.md)
- [RotationUtils reference](api-reference/RotationUtils.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)

[← Back to Home](../Home.md)

# `PatternMatcher`

Package: `net.astronomy.multilib.pattern`

> Detects whether a given `PatternManager` structure exists in the world.

A stateless utility class. You call `matches(...)` once per (placed block, candidate pattern) pair; MultiLib's internal placement listener does this for you, so most users never call this class directly. Read this page if you're debugging unexpected match/no-match behavior, or building something on top of MultiLib that needs to query matching outside the normal placement flow.

For the conceptual model (what "origin" means, layer/row/col → Y/Z/X mapping), read [Core Concepts](../Core-Concepts.md) and the [Rotation & Matching Deep Dive](../Rotation-And-Matching.md) first — this page is the method-level reference.

## Nested type: `MatchResult`

```java
public record MatchResult(BlockPos origin, PatternAction.TransformData transform) {}
```

- `origin` — the world position corresponding to the pattern's top-layer center cell, in the orientation that matched.
- `transform` — the [`TransformData`](PatternAction.md#nested-type-transformdata) describing exactly which rotation/orientation matched.

## Methods

### `matches(ServerLevel level, BlockPos placedPos, PatternManager pattern)`

```java
public static MatchResult matches(ServerLevel level, BlockPos placedPos, PatternManager pattern)
```

Attempts to find this pattern anchored around `placedPos`. Returns a `MatchResult` on success, or `null` if no orientation/position matches.

**Search strategy (high level):**

1. Iterate every layer, row, and column of the pattern that contains a recognized key character (skipping `' '` and unrecognized characters).
2. For each such cell, assume `placedPos` is *that* cell, and back-compute what the pattern's origin would have to be — first assuming the upright (non-vertical) orientation.
3. Try all 4 horizontal rotations at that candidate origin (`matchesWithAllTransforms`, `checkVertical = false`).
4. If `pattern.allowsVerticalRotation()` is `true`, **also** compute two more candidate origins per cell (one swapping Y↔Z, one swapping Y↔X — i.e. treating the placed block as if the structure were tipped onto its side around each axis) and retry all 4 rotations at each, this time with vertical checks enabled.
5. Return the **first** combination of (cell, candidate origin, orientation) that fully matches. Search order is: layers outer, then rows, then columns, then non-vertical before vertical-X before vertical-Z, then rotation `0→3` within each.

Because the search tries the placed block as *every* key cell of the pattern (not just specific anchor cells), placing **any** block that belongs to the pattern can trigger a successful match, as long as the rest of the structure is already present — not just the "last" block conceptually, though in practice the last block placed is what triggers the check (see [Core Concepts § Activation flow](../Core-Concepts.md#activation-flow)).

**Performance note:** this is a brute-force search — worst case roughly `(layers × rows × cols) × (1 + 2 × allowsVerticalRotation) × 4` full pattern comparisons, each of which is itself `O(pattern size)` block-state reads. For small patterns (a handful of blocks) this is negligible; for very large patterns with vertical rotation enabled, expect a noticeably larger cost per relevant block placement.

---

### `matchesWithAllTransforms(ServerLevel level, BlockPos origin, PatternManager pattern, boolean checkVertical)` *(private)*

Tries, in order: 4 horizontal rotations (always); if `checkVertical && pattern.allowsVerticalRotation()`, 4 more rotations with vertical-X, then 4 more with vertical-Z. Returns the first `MatchResult` found, or `null`.

> Listed here for completeness when reading stack traces / understanding search order — it's a private implementation detail, not part of the public API.

---

### `matchesTransformed(ServerLevel level, BlockPos origin, PatternManager pattern, int rotation, boolean vertical, String axis)`

```java
public static boolean matchesTransformed(ServerLevel level, BlockPos origin, PatternManager pattern,
                                          int rotation, boolean vertical, String axis)
```

The core block-by-block comparison. For every non-space, recognized key cell in the pattern:

1. Computes its relative offset `(relX, relY, relZ)` from the pattern's reference point (top layer, geometric center of each layer — see [Core Concepts](../Core-Concepts.md#layers-and-the-coordinate-system)).
2. If `vertical` is `true`, pre-rotates that offset 90° around `axis` (manual inline swap, **not** via `RotationUtils.rotateVertical` — see [Caveats](#caveats)).
3. Applies `RotationUtils.transform(relX, relY, relZ, rotation, false, axis)` (horizontal-only, since the vertical component was already applied in step 2) to get the final offset.
4. Reads `level.getBlockState(origin.offset(transformed))` and compares with `state.is(expectedBlock)`.
5. Returns `false` on the **first** mismatch found; `true` if every key cell matches.

This method is `public` and safe to call directly if you need to test "does this exact origin/rotation/axis combination match right now" without the full search — e.g. for a periodic structural-integrity check you implement yourself (MultiLib does not do this automatically, see [Core Concepts § Known Limitations](../Core-Concepts.md#known-limitations)).

**Block comparison is exact-block only:** `state.is(expected)` compares against the `Block`, not full `BlockState` (no property/blockstate matching — e.g. you cannot key on "any orientation of a stairs block" vs. a specific one beyond what `Block` identity already captures, and you cannot match block tags). If you need property-aware or tag-aware matching, you'll need to build it on top of this primitive yourself.

## Caveats

- **Unrecognized layer characters are silently skipped**, both during the outer search (`matches`) and the comparison (`matchesTransformed`) — a typo in a layer string that doesn't match any registered key is treated exactly like `' '` (no constraint), not as an error.
- **Step 2's inline vertical pre-rotation duplicates logic from `RotationUtils.rotateVertical`** rather than calling it — the two are kept in sync by hand in the source. If you're patching/forking the matcher, be aware these two implementations must agree.
- **`allowsHorizontalRotation()` is not consulted anywhere in this class** — see [Core Concepts § Known Limitations](../Core-Concepts.md#known-limitations).
- Matching always runs against **whatever blocks currently exist in the world** at lookup time — there is no "snapshot" or transactional guarantee; if blocks are placed/removed concurrently by other logic in the same tick, behavior is whatever the live world state happens to be at the moment each `getBlockState` call executes.

## See also

- [Core Concepts](../Core-Concepts.md)
- [Rotation & Matching Deep Dive](../Rotation-And-Matching.md)
- [RotationUtils](RotationUtils.md)
- [PatternAction](PatternAction.md) — consumes `MatchResult`

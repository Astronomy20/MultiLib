[← Back to Home](index.md)

# Rotation & Matching Deep Dive

Why matching behaves as it does, at the level of the matcher implementations. Read [Core Concepts](Core-Concepts.md) first.

> **Rotation is genuinely enforced.** In the old API the rotation flags were stored but never gated the search — every pattern was checked in all 4 horizontal rotations regardless. Now `RotationMode.NONE` really locks to the built orientation, and `.allowRotation(...)` gives per-axis control the matcher actually reads.

## Three matchers, one dispatcher

`PatternMatcher.matches(level, placedPos, definition)` dispatches by how the definition was built:

| Definition uses… | Matcher | Strategy |
|---|---|---|
| `.shapeless()` | `ShapelessMatcher` | Flood-fill from the placed block, then validate shell/interior/count |
| `.pattern(PatternProvider)` | `FunctionalMatcher` | Same search as shaped, reading cells from a provider |
| `.layer(...)` | `ShapedMatcher` | Text grid, searched across every allowed orientation |

The rest of this page is about `ShapedMatcher`. `FunctionalMatcher` reuses its `applyTransform(...)` geometry but derives rotations only from the coarse `RotationMode` — **it never reads `.allowRotation(...)`**, so granular rotation is shaped-only. `ShapelessMatcher` doesn't rotate (a blob has no orientation); it always reports `TransformData(0, false, "NONE")`.

## Search, don't compute

MultiLib doesn't compute where a pattern could be — it **searches**. For the placed block, it tries every symbol cell as a hypothesis ("what if this block is the cell at offset (1,0,−1), in this orientation?"), derives the origin that hypothesis implies, and compares the whole pattern against the world there.

Two consequences:

1. **No dedicated trigger block needed** — any block matching the activation symbol, placed as the last piece, can trigger a match.
2. **Cost scales with pattern size × orientations**, not world size. `HORIZONTAL` tries ≤4 rotations per candidate cell; `ALL` tries 20 (see below) — enable it only if the structure must be recognized on its side.

## Coordinate recap

For a cell at `(col, row)` in layer `layerIndex` (0 = first `.layer(...)`):

```
relX = col - centerX     centerX = layer.width / 2
relY = (layersCount - 1) - layerIndex
relZ = row - centerZ     centerZ = layer.height / 2
```

`relY` is `0` for the first (top) layer and increasingly negative downward. The `origin` always corresponds to the top-layer center cell, in whatever orientation matched.

## What the rotation settings gate

`ShapedMatcher` derives two booleans:

```java
boolean allowHorizontal = !allowedRotations.isEmpty()
        ? true
        : definition.getRotationMode() != RotationMode.NONE;
boolean allowVertical = !allowedRotations.isEmpty()
        ? allowedRotations.stream().anyMatch(ar -> ar.axis() != RotationAxis.Y)
        : definition.getRotationMode() == RotationMode.ALL;
```

- Any `.allowRotation(...)` call **takes over** from `RotationMode`: the unrotated orientation plus exactly the axis/angle combinations you declared, nothing more.
- Otherwise `RotationMode` applies: `NONE` = rotation 0 around Y only; `HORIZONTAL` = all 4 Y rotations; `ALL` = also the tipped orientations below.

## Tipped-over orientations

With vertical rotation enabled, the matcher reinterprets which world axis plays the pattern's Y axis, via `applyTransform`:

| `axis` | Tip | Spin |
|---|---|---|
| `"X"` | 90° around Z | `rotation*90°` around X |
| `"X_FLIP"` | 270° around Z | `rotation*90°` around X |
| `"Z"` | 90° around X | `rotation*90°` around Z |
| `"Z_FLIP"` | 270° around X | `rotation*90°` around Z |
| `"Y"` | none | `rotation*90°` around Y |

Each transform composes a fixed **tip** (the layer axis onto the target axis) with a **spin** (0–3 steps around it) — both proper rotations via [`RotationUtils.rotate`](api-reference/RotationUtils.md), so chirality stays correct. `X_FLIP`/`Z_FLIP` tip the opposite way (onto the negative axis), catching a structure tipped either direction. Full vertical rotation = up to **20 orientations** per candidate cell: 4 upright + 4 each for X, Z, X_FLIP, Z_FLIP.

## Per-transform origin

Each candidate transform gets its own origin:

```java
BlockPos origin = activationPos.offset(-applyTransform(relX, relY, relZ, axis, rotation));
```

A rotated structure only lines up against the origin computed *for that same rotation* — testing it against a rotation-0 origin (as old code did) could never match anything but the identity orientation.

## The result: `TransformData`

```java
public record TransformData(int rotation, boolean vertical, String axis) {}
```

- `axis` — `"Y"` (flat), `"X"`/`"X_FLIP"`/`"Z"`/`"Z_FLIP"` (tipped), or `"NONE"` (shapeless).
- `rotation` — the 0–3 step (×90°) around `axis`.
- `vertical` — `axis != "Y"`.

`MultiblockInstance.getTransform()` exposes this — e.g. for an `onFormed` callback that faces a spawned effect the right way.

## Granular rotation

Use `.allowRotation(...)` for asymmetric per-axis control:

```java
.allowRotation(RotationAxis.Y, 180)
.allowRotation(RotationAxis.X, 90, 270)
```

Each entry is tried in addition to the identity orientation; declaring any switches the matcher to the granular path — the two paths aren't combined.

## Free blocks

`freeBlock(...)` positions are scanned after a candidate orientation's fixed cells match, in that transform's coordinate space — so they rotate with the structure, no special handling.

## See also

- [Core Concepts](Core-Concepts.md), [RotationUtils](api-reference/RotationUtils.md), [MultiblockBuilder § Rotation](api-reference/MultiblockBuilder.md#rotation), [Pattern Design Guide](Pattern-Design-Guide.md)

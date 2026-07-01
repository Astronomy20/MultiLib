[← Back to Home](Home.md)

# Rotation & Matching Deep Dive

This page explains *why* matching behaves the way it does, at the level of the actual matcher implementations. Read [Core Concepts](Core-Concepts.md) first if you haven't.

⚠️ This entire matching system was rewritten from the old `PatternMatcher`. Most importantly: **rotation is now genuinely enforced.** In the old API, `allowHorizontalRotation`/`allowVerticalRotation` were stored but never actually gated the search — every pattern was checked in all 4 horizontal rotations regardless of the flag, and there was no way to lock a structure to a single facing. That bug is fixed: `RotationMode.NONE` now really does mean "only the exact placed orientation matches," and the granular `.allowRotation(...)` API gives you per-axis control that's actually read by the matcher.

## Three matchers, one dispatcher

`PatternMatcher.matches(level, placedPos, definition)` is a thin dispatcher — it picks one of three implementations based on how the definition was built:

| Definition uses... | Matcher | Strategy |
|---|---|---|
| `.shapeless()` | `ShapelessMatcher` | Flood-fill from the placed block, then validate shell/interior/count constraints |
| `.pattern(PatternProvider)` | `FunctionalMatcher` | Same search structure as `ShapedMatcher`, but reads cells from a procedural provider instead of a text grid |
| plain `.layer(...)` | `ShapedMatcher` | Textual grid, searched across every allowed orientation |

The rest of this page focuses on `ShapedMatcher`, since it's what most structures use and where rotation search is most involved. `FunctionalMatcher` reuses `ShapedMatcher.applyTransform(...)` for the tip/spin geometry and origin-per-candidate computation, but it only derives *which* rotations to try from the coarse `RotationMode` (`NONE`/`HORIZONTAL`/`ALL`) — it never reads `.allowRotation(...)`'s granular per-axis declarations. So the granular rotation API described later on this page is `ShapedMatcher`-only; procedural (`.pattern(...)`) structures can only use `RotationMode`. `ShapelessMatcher` doesn't rotate at all — a flood-filled blob has no fixed orientation to rotate, so it always reports `TransformData(0, false, "NONE")`.

## The mental model: search, don't compute

MultiLib does not compute "where could this pattern be" analytically. It **searches**: for the placed block, it tries every symbol cell of the pattern as a hypothesis ("what if *this* placed block is the diamond block at relative offset (1, 0, -1), in *this* orientation?"), derives what the pattern's origin would have to be under that hypothesis, and brute-force compares the whole pattern against the world at that origin.

Two practical consequences:

1. **You don't need to place a specific "trigger" block.** Any block matching the *activation* symbol, placed as the structure's last missing piece, can trigger a successful match.
2. **Cost scales with pattern size × orientations tried**, not with world size. `RotationMode.HORIZONTAL` (the default) tries at most 4 rotations per candidate cell; `RotationMode.ALL` tries 20 (4 upright + 4×4 tipped, see below) — noticeably more expensive, only enable it if the structure genuinely needs to be recognized on its side.

## Coordinate space recap

For a cell at `(col, row)` in layer index `layerIndex` (0 = first `.layer(...)` call):

```
relX = col - centerX     centerX = layer.width / 2
relY = (layersCount - 1) - layerIndex
relZ = row - centerZ     centerZ = layer.height / 2
```

`relY` is `0` for the **first** `.layer(...)` call (the top) and increasingly negative for each subsequent call — confirming the ⚠️ top-first layer order from [Core Concepts](Core-Concepts.md#layers-and-the-coordinate-system). The `origin` in a `MatchResult`/callback context always corresponds to this top-layer center cell, in whichever orientation actually matched.

## What `RotationMode` and `allowRotation(...)` actually gate

`ShapedMatcher` derives two booleans from the definition before searching:

```java
boolean allowHorizontal = !allowedRotations.isEmpty()
        ? true
        : definition.getRotationMode() != RotationMode.NONE;
boolean allowVertical = !allowedRotations.isEmpty()
        ? allowedRotations.stream().anyMatch(ar -> ar.axis() != RotationAxis.Y)
        : definition.getRotationMode() == RotationMode.ALL;
```

- If you called `.allowRotation(...)` at all, that **takes over entirely** from `RotationMode` — the unrotated orientation is always tried as a baseline, plus exactly the axis/angle combinations you declared (`tryGranularTransformsForCell`), nothing more.
- Otherwise, the coarse `RotationMode` applies: `NONE` tries only rotation 0 around Y; `HORIZONTAL` tries all 4 Y-axis rotations; `ALL` additionally tries the tipped-over orientations described below.

`RotationMode.NONE` genuinely restricts matching to the exact built orientation now — no manual workaround needed (unlike the old API).

## Vertical rotation: what "tipped over" means geometrically

When vertical rotation is enabled (`RotationMode.ALL`, or an `.allowRotation(...)` entry on `RotationAxis.X`/`RotationAxis.Z`), the matcher doesn't just rotate the *search origin* — it reinterprets which world axis plays the role of the pattern's Y axis, via `ShapedMatcher.applyTransform`:

| `axis` | Tip step | Spin step |
|---|---|---|
| `"X"` | rotate 90° around Z | then rotate `rotation*90°` around X |
| `"X_FLIP"` | rotate 270° around Z | then rotate `rotation*90°` around X |
| `"Z"` | rotate 90° around X | then rotate `rotation*90°` around Z |
| `"Z_FLIP"` | rotate 270° around X | then rotate `rotation*90°` around Z |
| `"Y"` (or any other value) | no tip needed | rotate `rotation*90°` around that axis |

Each transform composes a fixed **tip** (rotating the layer-stacking axis onto the target axis) with a **spin** (the 0–3 `rotation` step around that now-vertical axis) — both are proper rotations via [`RotationUtils.rotate`](api-reference/RotationUtils.md), not coordinate swaps or reflections, so orientation/chirality stays correct. `X_FLIP`/`Z_FLIP` tip the opposite way, landing the layer axis on the *negative* target axis instead of the positive one — this is what lets the matcher recognize a structure tipped either of the two ways onto a given axis, not just one.

With full vertical rotation enabled, a pattern is checked in up to **20 orientations** per candidate cell: 4 upright (`Y`) + 4 each for `X`, `Z`, `X_FLIP`, `Z_FLIP`.

⚠️ A structure "tipped over" this way changes which of its declared layers ends up horizontal vs. vertical in the world — if your structure isn't actually meaningful lying on its side, leave `RotationMode` at `HORIZONTAL` (the default) rather than `ALL`.

## Per-transform origin (the key correctness fix)

For every candidate cell and every transform being tried, the origin is computed **specifically for that transform**:

```java
BlockPos origin = activationPos.offset(-applyTransform(relX, relY, relZ, axis, rotation));
```

This matters: a structure actually built in a rotated orientation only lines up against the origin computed *for that same rotation* — testing it against an origin computed under the rotation=0 assumption (as older matcher code did) could never find a match for anything but the identity orientation. Every rotation candidate now gets its own correctly-derived origin before the grid comparison runs.

## Reading the result: `TransformData`

```java
public record TransformData(int rotation, boolean vertical, String axis) {}
```

- `axis` — `"Y"` (flat rotation), `"X"`/`"X_FLIP"`/`"Z"`/`"Z_FLIP"` (tipped), or `"NONE"` (shapeless structures, which don't rotate).
- `rotation` — the 0–3 step (×90°) applied around `axis`.
- `vertical` — `axis != "Y"`.

`MultiblockInstance.getTransform()` exposes this for anything that needs to know the built orientation after the fact (e.g. an `onFormed` callback deciding which way to face a spawned effect).

## Granular rotation: `allowRotation(...)`

Use this instead of `RotationMode` when you need asymmetric per-axis control — e.g. allow a 180° flip around Y but no 90°/270°, or allow tipping on X but not Z:

```java
.allowRotation(RotationAxis.Y, 180)
.allowRotation(RotationAxis.X, 90, 270)
```

Each `AllowedRotation(axis, angle)` is tried in addition to the always-tried identity orientation; declaring any entry switches the matcher into the granular path (`tryGranularTransformsForCell`) instead of the coarse `RotationMode` path — the two are not combined.

## Free blocks and rotation

`freeBlock(...)` positions are scanned *after* a candidate orientation's fixed cells all match (`scanFreeBlocks`), within that same transform's coordinate space — so free blocks rotate along with the rest of the structure; there's no separate rotation handling needed for them.

## See also

- [Core Concepts](Core-Concepts.md)
- [RotationUtils reference](api-reference/RotationUtils.md)
- [MultiblockBuilder § Rotation](api-reference/MultiblockBuilder.md#rotation)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Advanced Features § Shapeless structures, Procedural patterns](Advanced-Features.md)

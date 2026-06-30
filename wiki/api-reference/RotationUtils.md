[← Back to Home](../Home.md)

# `RotationUtils`

Package: `net.astronomy.multilib.utils`

> Utility for rotating and mirroring coordinates around different axes.

A stateless math helper used internally by [`PatternMatcher`](PatternMatcher.md) and [`PatternAction.clearStructure`](PatternAction.md#clearstructureserverlevel-level-blockpos-origin-patternmanager-pattern-transformdata-transform) to transform a relative coordinate offset by a given rotation. You generally won't call this directly unless you're implementing custom matching/placement logic of your own on top of MultiLib's primitives.

## Methods

### `transform(int x, int y, int z, int rotation, boolean vertical, String axis)`

```java
public static int[] transform(int x, int y, int z, int rotation, boolean vertical, String axis)
```

Entry point combining both rotation kinds:

- If `vertical` is `true`, applies `rotateVertical(x, y, z, axis, rotation)` (full 3D rotation around `axis`).
- Otherwise, applies `rotate(x, z, rotation)` (2D horizontal rotation around Y) and leaves `y` unchanged.

**Returns:** `{newX, newY, newZ}`.

> Note the asymmetry: when `vertical` is `true`, the *entire* transform comes from `rotateVertical` (which itself can encode a horizontal-looking component depending on `axis`); the two rotation kinds aren't composed/stacked, one or the other is applied.

---

### `rotate(int x, int z, int rotation)`

```java
public static int[] rotate(int x, int z, int rotation)
```

Rotates an `(x, z)` offset around the Y axis in 90° increments.

| `rotation` | Result `{x, z}` |
|---|---|
| `0` (default/else) | `{x, z}` |
| `1` | `{z, -x}` |
| `2` | `{-x, -z}` |
| `3` | `{-z, x}` |

---

### `rotateVertical(int x, int y, int z, String axis, int rotation)`

```java
public static int[] rotateVertical(int x, int y, int z, String axis, int rotation)
```

Rotates a full `(x, y, z)` offset around the `X` or `Z` axis in 90° increments. `rotation` is normalized with `% 4` internally.

**`axis = "X"`:**

| `rotation % 4` | Result |
|---|---|
| `0` | unchanged |
| `1` | `y' = -z`, `z' = y` |
| `2` | `y' = -y`, `z' = -z` |
| `3` | `y' = z`, `z' = -y` |

**`axis = "Z"`:**

| `rotation % 4` | Result |
|---|---|
| `0` | unchanged |
| `1` | `y' = x`, `x' = -y` |
| `2` | `y' = -y`, `x' = -x` |
| `3` | `y' = -x`, `x' = y` |

Any other `axis` value (including `"Y"`) leaves the coordinate unchanged — `rotateVertical` has no horizontal-rotation behavior of its own; that's what `rotate(...)` is for.

`axis` is matched case-insensitively (`axis.toUpperCase()`).

## See also

- [PatternMatcher](PatternMatcher.md) — the main consumer of this class, see its deep dive for how rotation values are chosen during search
- [Rotation & Matching Deep Dive](../Rotation-And-Matching.md)

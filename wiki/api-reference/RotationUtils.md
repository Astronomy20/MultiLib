[← Back to Home](../index.md)

# `RotationUtils`

Package: `net.astronomy.multilib.util`

> Utility for rotating 3D coordinates around any of the three axes (X, Y, Z), in 90° steps.

A stateless math helper used internally by [`ShapedMatcher`](../Rotation-And-Matching.md) to compute per-transform origins and offsets. You generally won't call this directly unless you're implementing custom matching/placement logic of your own on top of MultiLib's primitives.

⚠️ **This is a completely different signature from the old API.** The old two-method split (`rotate(x, z, rotation)` for horizontal + `rotateVertical(x, y, z, axis, rotation)` for vertical) is gone. There is now a single unified method that rotates around **any** axis, and it takes an **angle in degrees**, not a `0–3` step index.

## Methods

### `rotate(int x, int y, int z, String axis, int angle)`

```java
public static int[] rotate(int x, int y, int z, String axis, int angle)
```

Rotates the full `(x, y, z)` offset around a single axis by `angle` degrees.

- `axis` - `"X"`, `"Y"`, or `"Z"` (case-insensitive). Any other value returns the input unchanged.
- `angle` - degrees; normalized internally via `((angle % 360) + 360) % 360` then divided into 90° steps, so any multiple of 90 (including negative values) works. Non-multiples of 90 are truncated toward the next lower step (integer division), not rejected.

**Returns:** `{newX, newY, newZ}`.

Rotation directions (right-handed, looking down the positive axis toward the origin):

**`axis = "X"`:**

| step (angle / 90) | Result |
|---|---|
| 0 (0°) | unchanged |
| 1 (90°) | `y' = -z`, `z' = y` |
| 2 (180°) | `y' = -y`, `z' = -z` |
| 3 (270°) | `y' = z`, `z' = -y` |

**`axis = "Y"`:**

| step | Result |
|---|---|
| 0 (0°) | unchanged |
| 1 (90°) | `x' = -z`, `z' = x` |
| 2 (180°) | `x' = -x`, `z' = -z` |
| 3 (270°) | `x' = z`, `z' = -x` |

**`axis = "Z"`:**

| step | Result |
|---|---|
| 0 (0°) | unchanged |
| 1 (90°) | `x' = y`, `y' = -x` |
| 2 (180°) | `x' = -x`, `y' = -y` |
| 3 (270°) | `x' = -y`, `y' = x` |

`Y`-axis rotation is what the old API called "horizontal rotation"; `X`/`Z`-axis rotation is what the old API split out as "vertical rotation." Both are now the same method, just a different `axis` argument - there's no longer a separate code path or a `vertical` boolean to pass.

## See also

- [Rotation & Matching Deep Dive](../Rotation-And-Matching.md) - how `ShapedMatcher` composes rotation axis + angle with `RotationMode`/`AllowedRotation` to search candidate orientations
- [MultiblockBuilder § Rotation](MultiblockBuilder.md#rotation)

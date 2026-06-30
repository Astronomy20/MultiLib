[← Back to Home](../Home.md)

# `PatternAction`

Package: `net.astronomy.multilib.pattern`

> Defines what happens when a pattern is matched.

`@FunctionalInterface` — the code you write per-pattern to react to a successful match. Set via [`PatternBuilder.action(...)`](PatternBuilder.md#actionpatternaction-action).

## Functional method

### `onMatch(ServerLevel level, BlockPos origin)`

```java
void onMatch(ServerLevel level, BlockPos origin);
```

The method you implement with a lambda or method reference in the common case. `origin` is the world position corresponding to the pattern's **top layer's center cell** (relative offset `(0, 0, 0)` in pattern space — see [Core Concepts](../Core-Concepts.md#layers-and-the-coordinate-system)), already adjusted for whatever rotation/orientation matched.

```java
.action((level, origin) -> {
    level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
})
```

## Default method

### `onMatch(ServerLevel level, BlockPos origin, TransformData transform)`

```java
default void onMatch(ServerLevel level, BlockPos origin, TransformData transform) {
    onMatch(level, origin);
}
```

Override this instead of the two-argument overload if your action needs to know **which orientation** matched (e.g. to reject orientations your gameplay logic doesn't want — see the `allowHorizontalRotation` caveat in [Core Concepts](../Core-Concepts.md#known-limitations)), or to pass the transform into `clearStructure(...)`.

This is the overload MultiLib's internal placement handler actually calls; the two-argument version exists purely as the default/simple case.

## Nested type: `TransformData`

```java
record TransformData(int rotation, boolean vertical, String axis) {}
```

Describes the exact transformation applied to the pattern's coordinate space to make it match the world:

| Field | Meaning |
|---|---|
| `rotation` | Number of 90° clockwise turns applied (`0`–`3`) |
| `vertical` | Whether a vertical (X/Z-axis) rotation was applied in addition to the horizontal one |
| `axis` | `"X"`, `"Z"`, or `"Y"` — which axis the vertical rotation (if any) was performed around. `"Y"` is used as the placeholder value when `vertical` is `false` |

## Static helpers

### `clearStructure(ServerLevel level, BlockPos origin, PatternManager pattern, TransformData transform)`

```java
static void clearStructure(ServerLevel level, BlockPos origin, PatternManager pattern, TransformData transform)
```

Removes every block belonging to the pattern from the world (`level.removeBlock(pos, false)` for each non-space cell across all layers), using the **exact** `origin`/`transform` pair returned by the match — this guarantees the removal targets the same orientation that was actually found, not just the upright/default one.

Typical usage from inside an action when the structure should be "consumed":

```java
.action((level, origin, transform) -> {
    // ... spawn loot, play effects, etc., using origin ...
    PatternAction.clearStructure(level, origin, myPattern, transform);
})
```

> Note the action lambda needs a reference to the same `PatternManager` it was attached to (`myPattern` above) — keep a reference to the built pattern if your action needs to clear it.

---

### `spawnParticles(ServerLevel level, BlockPos origin)`

```java
static void spawnParticles(ServerLevel level, BlockPos origin)
```

Convenience helper: spawns 10 `END_ROD` particles centered roughly one block above `origin`. Useful as a placeholder/quick-feedback effect while prototyping a pattern.

---

### `playSound(ServerLevel level, BlockPos origin)`

```java
static void playSound(ServerLevel level, BlockPos origin)
```

Convenience helper: plays `SoundEvents.AMETHYST_BLOCK_CHIME` at `origin` via `SoundSource.BLOCKS` at volume/pitch `1.0F`. Also intended as a quick placeholder effect, not a production-ready sound choice for every use case.

## Writing your own action: practical notes

- **Runs server-side only.** `ServerLevel` is the only level type passed in — there is no client-side hook for pattern matches.
- **Runs synchronously on the server thread**, inside the block placement event. Avoid heavy/blocking work in `onMatch`; schedule follow-up work for later ticks if needed.
- **Fires once per match.** There's no "structure broken" companion callback — if your action represents a transformation, your action is responsible for tracking and removing the resulting state when appropriate.
- If you need the matched origin **and** the orientation, implement the three-argument `onMatch` overload rather than parsing rotation back out yourself.

## See also

- [Core Concepts § Activation flow](../Core-Concepts.md#activation-flow)
- [PatternMatcher](PatternMatcher.md) — produces the `MatchResult` (origin + `TransformData`) consumed here

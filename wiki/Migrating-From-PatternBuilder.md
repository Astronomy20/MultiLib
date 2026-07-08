[← Back to Home](index.md)

# Migrating from the old PatternBuilder API

The matching/registration system was rewritten. `PatternBuilder`, `PatternManager`, the old `PatternMatcher`, `PatternRegistry`, and `PatternAction` are gone — everything goes through `MultiblockBuilder`/`MultiblockDefinition` via `MultiLibAPI.define(id)`. This is a porting checklist; read [Core Concepts](Core-Concepts.md) first if the new API is unfamiliar.

## Class mapping

| Old | New |
|---|---|
| `PatternManager.pattern()` | `MultiLibAPI.define(id)` |
| `PatternBuilder` | `MultiblockBuilder` |
| `PatternMatcher` (one algorithm) | `PatternMatcher` dispatcher → `ShapedMatcher` / `ShapelessMatcher` / `FunctionalMatcher` |
| `PatternRegistry` | `MultiblockRegistry` (+ `BlockDefinitionRegistry`) |
| `PatternAction` | `.onFormed(...)` / `.onBroken(...)` |
| — | `MultiblockInstance` + `WorldMultiblockTracker` (persistent tracking) |

## Checklist

### 1. Layer order is reversed ⚠️

The most common mistake. Old: first `.layer(...)` = **bottom**. New: first `.layer(...)` = **top**. Just reverse your `.layer(...)` call order — row/column ordering within a layer is unchanged.

### 2. `key(char, Block)` still works

It now wraps `BlockIngredient.of(block)`, so these lines usually need no change. You can also bind tags, state-specific matches, or predicates now — `BlockIngredient.anyOf(...)`/`.tag(...)` often replaces old near-duplicate patterns ([reference](api-reference/BlockIngredient.md)).

### 3. `.action(...)` → `.onFormed(...)` / `.onBroken(...)`

```java
// OLD
.action((level, origin, transform) -> { ... })

// NEW
.onFormed(ctx -> {
    Level level = ctx.level();
    BlockPos origin = ctx.instance().getOrigin();
    TransformData transform = ctx.instance().getTransform();
    ...
})
```

- `.onBroken(...)` is new — nothing could react to teardown before.
- Both can be called multiple times; all callbacks run in order.
- `ctx.instance()` replaces bare `origin`/`transform` and also gives `getPositions()`, `getPositionsFor(symbol)`, `getCorePos()`, and a stable `UUID`.

### 4. `.build()` always registers

Old: a pattern registered only if `.action(...)` was set. New: `.build()` always registers. Use `.buildWithoutRegistering()` for an object without registration (e.g. tests).

### 5. Rotation flags: renamed and now enforced ⚠️

The old `allowHorizontalRotation`/etc. flags were stored but never read — all 4 horizontal rotations were always tried. Now:

```java
.rotations(RotationMode.NONE)        // genuinely locks to the built orientation
.rotations(RotationMode.HORIZONTAL)  // default — 4 Y rotations (old always-on behavior)
.rotations(RotationMode.ALL)         // Y + tipped (replaces allowVerticalRotation(true))

.allowRotation(RotationAxis.Y, 180)      // granular — no old equivalent
.allowRotation(RotationAxis.X, 90, 270)
```

Delete any manual `if (transform.rotation() != 0) return;` guard and use `RotationMode.NONE`. `allowSideRotation`/`allowUpsideDown` had no distinct behavior; use `.allowRotation(RotationAxis.X/Z, ...)` for finer vertical control.

### 6. `RotationUtils` signature change

```java
// OLD                                    // NEW — one method, degrees
RotationUtils.rotate(x, z, rotation);      RotationUtils.rotate(x, y, z, axis, angle);
RotationUtils.rotateVertical(x,y,z,axis,rotation);
```

Axis is now required for horizontal too (`"Y"`), and the angle is degrees, not a 0–3 step ([reference](api-reference/RotationUtils.md)). You rarely call this directly.

### 7. `clearStructure(...)` is gone

Formation never mutates the world. To clear blocks yourself, iterate `ctx.instance().getPositions()`. For a "collapses into one block" *look*, use `.model(...)` ([Master-Dummy](Advanced-Features.md#master-dummy-model)).

### 8. Priority replaces registration order

The old registry had no priority — first-registered won. `MultiblockRegistry` sorts by `.priority(int)` (descending), JSON before Java on ties; registration order no longer matters. Give the more important competing definition a higher `.priority(...)`.

## New capabilities (nothing to migrate)

May replace old workarounds: persistent instance tracking + `.onBroken(...)`; controller/part base classes; pluggable `BlockIngredient`; `shapeless()`, procedural, and JSON definitions; ghost overlay, auto-place, `.mainFace()`, IO ports, wall sharing; JEI/REI/EMI/Patchouli. See [Advanced Features](Advanced-Features.md).

## See also

- [Core Concepts § What changed](Core-Concepts.md#what-changed-from-the-old-api-summary), [Rotation & Matching](Rotation-And-Matching.md), [Getting Started](Getting-Started.md)

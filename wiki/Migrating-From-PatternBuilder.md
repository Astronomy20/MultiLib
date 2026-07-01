[← Back to Home](Home.md)

# Migrating from the old PatternBuilder API

MultiLib's matching/registration system was completely rewritten. `PatternBuilder`, `PatternManager`, `PatternMatcher` (old), `PatternRegistry`, and `PatternAction` no longer exist — everything goes through `MultiblockBuilder`/`MultiblockDefinition` now (via `MultiLibAPI.define(id)`). This page is a concrete checklist for porting old code, not a conceptual introduction — read [Core Concepts](Core-Concepts.md) first if you haven't used the new API at all.

## Class-by-class mapping

| Old | New |
|---|---|
| `PatternManager.pattern()` | `MultiLibAPI.define(ResourceLocation id)` |
| `PatternBuilder` | `MultiblockBuilder` |
| `PatternMatcher` (static, one shaped algorithm) | `PatternMatcher` (dispatcher) → `ShapedMatcher` / `ShapelessMatcher` / `FunctionalMatcher` |
| `PatternRegistry` | `MultiblockRegistry` (+ `BlockDefinitionRegistry` for block-level metadata) |
| `PatternAction` | `MultiblockFormedCallback`/`MultiblockBrokenCallback` (`.onFormed(...)`/`.onBroken(...)`) |
| — (nothing) | `MultiblockInstance` + `WorldMultiblockTracker` — structures are now persistently tracked, not one-shot |

## Checklist

### 1. Layer order is reversed

⚠️ The single most common porting mistake. Old API: first `.layer(...)` call = **bottom**. New API: first `.layer(...)` call = **top**.

```java
// OLD — bottom first
.layer("BBB", "BBB", "BBB")   // Y=0 (bottom)
.layer(" G ", " G ", " G ")   // Y=2 (top)

// NEW — top first
.layer(" G ", " G ", " G ")  // top
.layer("BBB", "BBB", "BBB")  // bottom
```

Simply **reverse the order of your `.layer(...)` calls** — the name is unchanged, but the row/column ordering within a layer (row → Z, column → X) is unchanged too, only the call order flips.

### 2. `key(char, Block)` → still works, but ingredients are now pluggable

`.key(char, Block)` is still a valid shorthand — it now wraps `BlockIngredient.of(block)` internally, so this line often needs no change at all. But you can now also bind a symbol to a tag, a blockstate-specific match, or arbitrary logic — see the [`BlockIngredient` reference](api-reference/BlockIngredient.md) if your old pattern would have benefited from that (e.g. you were previously registering near-duplicate patterns just to accept a few interchangeable blocks — `BlockIngredient.anyOf(...)` or `BlockIngredient.tag(...)` likely replaces that duplication).

### 3. `.action(...)` → `.onFormed(...)` / `.onBroken(...)`

The old single `PatternAction` callback (fired once, on match, with `(level, origin, transform)`) is replaced by richer, multi-callback context objects:

```java
// OLD
.action((level, origin, transform) -> {
    // ... your logic ...
})

// NEW
.onFormed(ctx -> {
    Level level = ctx.level();
    BlockPos origin = ctx.instance().getOrigin();
    TransformData transform = ctx.instance().getTransform();
    // ... your logic ...
})
```

- You can now also register `.onBroken(...)` — there was no equivalent at all before; formed structures were never tracked, so nothing could react to being taken apart. This is one of the biggest capability upgrades: see [Callbacks & Events](api-reference/Callbacks-And-Events.md).
- `.onFormed(...)`/`.onBroken(...)` can each be called **multiple times** — every registered callback runs, in order. The old API only ever had one action.
- `ctx.instance()` (a `MultiblockInstance`) replaces the old bare `origin`/`transform` parameters — it also gives you `getPositions()`, `getPositionsFor(symbol)`, `getCorePos()`, and a stable `UUID` for the formed structure.

### 4. `.build()` always registers now

Old behavior: a pattern only got registered into `PatternRegistry` if `.action(...)` had been set — a pattern built without an action was silently inert. New behavior: **`.build()` always attempts registration**, whether or not you set any callbacks. If you had code relying on "no action = not registered" as an implicit disable switch, that no longer works — use `.buildWithoutRegistering()` if you genuinely want a `MultiblockDefinition` object without touching the registry (e.g. for tests).

### 5. Rotation flags: renamed and now actually enforced

⚠️ This is a correctness fix, not just a rename. The old `allowHorizontalRotation`/`allowVerticalRotation`/`allowSideRotation`/`allowUpsideDown` flags were **stored but never read by the matcher** — all 4 horizontal rotations were always tried regardless of what you set, and there was no way to lock a structure to one facing without manually rejecting orientations inside your action.

```java
// OLD — these flags did nothing to the actual search
.allowHorizontalRotation(false)  // had NO effect — all 4 rotations always tried anyway
.allowVerticalRotation(true)     // this one DID work — enabled 8 extra tipped orientations

// NEW — coarse control, actually enforced
.rotations(RotationMode.NONE)        // genuinely restricts to the exact built orientation
.rotations(RotationMode.HORIZONTAL)  // default — all 4 Y-axis rotations (equivalent to the old always-on behavior)
.rotations(RotationMode.ALL)         // Y-axis + tipped-over orientations (replaces allowVerticalRotation(true))

// NEW — granular control (no old equivalent)
.allowRotation(RotationAxis.Y, 180)           // only a 180° flip, not 90°/270°
.allowRotation(RotationAxis.X, 90, 270)       // tip onto X, but only these two angles
```

If your old code had a manual `if (transform.rotation() != 0) return;` guard inside its action to fake a fixed facing, you can now delete that workaround and use `.rotations(RotationMode.NONE)` instead — it actually works.

`allowSideRotation`/`allowUpsideDown` have no direct replacement because they never did anything distinguishable from `allowVerticalRotation` in the old matcher — if you need finer-grained vertical control than `RotationMode.ALL` gives you, use `.allowRotation(RotationAxis.X, ...)`/`.allowRotation(RotationAxis.Z, ...)` with specific angles.

### 6. `RotationUtils` signature change

```java
// OLD — two separate methods
RotationUtils.rotate(x, z, rotation);                          // horizontal only, 0-3 step index
RotationUtils.rotateVertical(x, y, z, axis, rotation);          // vertical, 0-3 step index

// NEW — one unified method, angle in degrees
RotationUtils.rotate(x, y, z, axis, angle);   // any axis ("X"/"Y"/"Z"), angle in degrees (multiples of 90)
```

See the [`RotationUtils` reference](api-reference/RotationUtils.md) for the full rotation tables. You almost certainly don't call this directly (it's an internal matcher helper), but if you had custom code built on top of it, the axis parameter is now required for horizontal rotation too (`"Y"`), and the rotation argument is degrees, not a 0–3 step.

### 7. `PatternAction.clearStructure(...)` has no direct replacement

The old API had a helper to manually clear every pattern block after your action ran (since formed structures weren't tracked, "consuming" a structure was entirely on you). The new API doesn't remove blocks automatically either — formation never mutates the world — but you now have `ctx.instance().getPositions()` to iterate and clear yourself if you want that behavior, or you can use `.model(...)` (Master-Dummy) if what you actually wanted was a "collapses into one block visually" effect rather than a structural removal — see [Advanced Features § Master-Dummy model](Advanced-Features.md#master-dummy-model).

### 8. No more first-match-wins ambiguity without a `priority`

The old registry had no concept of match priority — the first-registered pattern matching a placed block simply won, order depending on registration order alone. The new `MultiblockRegistry` sorts candidates by `.priority(int)` (descending) before falling back to a fixed tiebreak: **JSON-defined definitions win ties over Java-defined ones** (the same "data overrides hardcoded defaults" convention vanilla uses for recipes/loot tables/tags) — registration order is no longer a factor at all. If you had patterns competing over a shared key block and relied on registration order to resolve the ambiguity, give the more specific/important one an explicit higher `.priority(...)` instead.

## What has no equivalent to migrate — entirely new capability

These didn't exist in the old API at all, so there's nothing to "migrate," but they may replace workarounds you built around the old API's limitations:

- Persistent instance tracking (`MultiblockInstance`, `WorldMultiblockTracker`) and a real broken lifecycle (`.onBroken(...)`, `MultiblockBrokenEvent`).
- `AbstractMultiblockControllerBE`/`Block` and `AbstractMultiblockPartBE`/`Block` — state machine, model-hiding, part-membership tracking.
- Pluggable `BlockIngredient` (tags, predicates, state-specific matches).
- `shapeless()`, `PatternProvider`-backed procedural shapes, JSON/datapack definitions.
- Ghost overlay, auto-place, `.mainFace()` fixed-facing cores, IO ports, wall sharing.
- JEI/REI/EMI/Patchouli integration.

See [Advanced Features](Advanced-Features.md) for all of these.

## See also

- [Core Concepts § What changed from the old API (summary)](Core-Concepts.md#what-changed-from-the-old-api-summary)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [Getting Started](Getting-Started.md)

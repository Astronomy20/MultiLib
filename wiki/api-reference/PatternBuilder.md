[← Back to Home](../Home.md)

# `PatternBuilder`

Package: `net.astronomy.multilib.pattern`

> Builds a `PatternManager` safely. Supports automatic registration.

A mutable, fluent builder for assembling a [`PatternManager`](PatternManager.md). Every setter returns `this`, so calls chain. Obtain an instance via `new PatternBuilder()`, `PatternManager.pattern()`, or `MultiLibAPI.pattern()` — all three are equivalent.

See [Core Concepts](../Core-Concepts.md) for the meaning of keys, layers, the coordinate system, and the rotation flags. This page only documents the API surface.

## Default state

| Field | Default |
|---|---|
| `blockMap` | empty |
| `layers` | empty |
| `action` | `null` |
| `allowHorizontalRotation` | `true` |
| `allowVerticalRotation` | `false` |
| `allowSideRotation` | `false` |
| `allowUpsideDown` | `false` |

## Methods

### `key(char symbol, Block block)`

```java
public PatternBuilder key(char symbol, Block block)
```

Registers a character → block mapping used by subsequent (and previous) `.layer(...)` calls. Keys are global to the whole builder — there is no per-layer scope.

- Calling `key` again with the same `symbol` **overwrites** the previous mapping (it's backed by a `HashMap`).
- `' '` (space) is reserved internally as "no constraint" and should not be used as a key symbol — see [Core Concepts § Keys](../Core-Concepts.md#keys).

**Returns:** `this`.

---

### `layer(String... rows)`

```java
public PatternBuilder layer(String... rows)
```

Appends one horizontal (Y) slice to the pattern. Call order matters: the **first** call you make is the bottom-most layer, the **last** call is the top-most layer (and the Y reference layer used by the matcher). See [Core Concepts § Layers and the coordinate system](../Core-Concepts.md#layers-and-the-coordinate-system) for the full coordinate mapping.

- Every `String` argument in a single call must have the **same length** — the matcher derives layer width from the first row only, so mismatched lengths silently produce incorrect matching rather than an error.
- Can be called multiple times; each call adds one more layer (does not replace previous layers).

**Returns:** `this`.

---

### `action(PatternAction action)`

```java
public PatternBuilder action(PatternAction action)
```

Sets the callback invoked when this pattern is matched in the world. See [`PatternAction`](PatternAction.md).

> Calling `.build()` without ever calling `.action(...)` produces a valid `PatternManager`, but it will **not** be auto-registered into `PatternRegistry` (registration only happens when an action is present) — it will never be matched against world placements unless you register it yourself.

**Returns:** `this`.

---

### `allowHorizontalRotation(boolean value)`

```java
public PatternBuilder allowHorizontalRotation(boolean value)
```

Default `true`. Intended to control whether the pattern should match when rotated around the Y axis.

> ⚠️ **Known limitation:** this flag is currently **not read** by `PatternMatcher` — all four horizontal rotations are always attempted regardless of this value. See [Core Concepts § Known Limitations](../Core-Concepts.md#known-limitations).

**Returns:** `this`.

---

### `allowVerticalRotation(boolean value)`

```java
public PatternBuilder allowVerticalRotation(boolean value)
```

Default `false`. When `true`, the matcher additionally searches for the pattern tipped onto its side (rotated around the X/Z axis), not just upright. Required (must be `true`) if you enable `allowSideRotation` or `allowUpsideDown`.

**Returns:** `this`.

---

### `allowSideRotation(boolean value)`

```java
public PatternBuilder allowSideRotation(boolean value)
```

Default `false`. Refines the vertical-rotation search. Requires `allowVerticalRotation(true)`, enforced at `.build()` time.

**Returns:** `this`.

---

### `allowUpsideDown(boolean value)`

```java
public PatternBuilder allowUpsideDown(boolean value)
```

Default `false`. Refines the vertical-rotation search to include the upside-down orientation. Requires `allowVerticalRotation(true)`, enforced at `.build()` time.

**Returns:** `this`.

---

### `build()`

```java
public PatternManager build()
```

Validates and constructs the immutable [`PatternManager`](PatternManager.md).

**Validation performed:**

- Throws `IllegalStateException("Pattern must have at least one layer!")` if no `.layer(...)` call was made.
- Throws `IllegalStateException("Side rotation and upside-down rotation require vertical rotation to be enabled!")` if `allowSideRotation` or `allowUpsideDown` is `true` while `allowVerticalRotation` is `false`.

**Side effect:** if `.action(...)` was called, the resulting pattern is registered into [`PatternRegistry`](PatternRegistry.md) via `PatternRegistry.register(pattern, action)`. If no action was set, the pattern is **not** registered.

**Returns:** the built, immutable `PatternManager`.

## Minimal example

```java
PatternManager altar = PatternManager.pattern()
        .key('B', Blocks.STONE_BRICKS)
        .key('G', Blocks.GOLD_BLOCK)
        .layer("BBB", "BBB", "BBB")
        .layer(" G ", " G ", " G ")
        .action((level, origin) -> { /* ... */ })
        .build();
```

## See also

- [Core Concepts](../Core-Concepts.md)
- [PatternManager](PatternManager.md)
- [PatternAction](PatternAction.md)

[← Back to Home](../Home.md)

# `PatternManager`

Package: `net.astronomy.multilib.pattern`

The immutable, built representation of a pattern. You never construct this directly — get one from [`PatternBuilder.build()`](PatternBuilder.md#build).

The package-private constructor defensively copies its inputs (`Map.copyOf`, `List.copyOf`), so a `PatternManager` instance is fully immutable once built; mutating a `PatternBuilder` after calling `.build()` does not affect previously built instances.

## Static factory

### `pattern()`

```java
public static PatternBuilder pattern()
```

Shorthand for `new PatternBuilder()`. Equivalent to [`MultiLibAPI.pattern()`](MultiLibAPI.md#pattern).

## Instance methods

### `getBlockMap()`

```java
public Map<Character, Block> getBlockMap()
```

Returns the immutable key → block map used by this pattern.

---

### `getLayers()`

```java
public List<List<String>> getLayers()
```

Returns the pattern's layers in the order they were added (first = bottom, last = top — see [Core Concepts](../Core-Concepts.md#layers-and-the-coordinate-system)). Each inner `List<String>` is one layer's rows.

---

### `getAction()`

```java
public PatternAction getAction()
```

Returns the [`PatternAction`](PatternAction.md) configured via `.action(...)`, or `null` if none was set.

---

### `getLayerCount()`

```java
public int getLayerCount()
```

Returns `getLayers().size()`.

---

### `isKeyBlock(Block block)`

```java
public boolean isKeyBlock(Block block)
```

Returns `true` if `block` is the value of at least one entry in this pattern's key map. Used by [`PatternRegistry`](PatternRegistry.md) to index patterns by block.

---

### `getKeyBlocks()`

```java
public Set<Block> getKeyBlocks()
```

Returns the distinct set of all blocks referenced by this pattern's keys (i.e. `Set.copyOf(blockMap.values())`).

---

### `allowsHorizontalRotation()`

```java
public boolean allowsHorizontalRotation()
```

Returns the value set via `PatternBuilder.allowHorizontalRotation(...)`.

> ⚠️ Currently informational only — `PatternMatcher` does not consult this flag. See [Core Concepts § Known Limitations](../Core-Concepts.md#known-limitations).

---

### `allowsVerticalRotation()`

```java
public boolean allowsVerticalRotation()
```

Returns the value set via `PatternBuilder.allowVerticalRotation(...)`. Consulted by `PatternMatcher` to decide whether to search tipped-over orientations.

---

### `allowsSideRotation()`

```java
public boolean allowsSideRotation()
```

Returns the value set via `PatternBuilder.allowSideRotation(...)`.

---

### `allowsUpsideDown()`

```java
public boolean allowsUpsideDown()
```

Returns the value set via `PatternBuilder.allowUpsideDown(...)`.

## Equality and identity

`PatternManager` does not override `equals`/`hashCode`, so it uses reference (identity) equality. `PatternRegistry` stores patterns in a `HashMap<PatternManager, PatternAction>` keyed by identity — this is safe because each `PatternManager` instance is only ever created once by `.build()` and never reconstructed from equal data.

## See also

- [PatternBuilder](PatternBuilder.md) — how to construct one
- [PatternMatcher](PatternMatcher.md) — how a `PatternManager` is matched against the world
- [PatternRegistry](PatternRegistry.md) — how built patterns are looked up

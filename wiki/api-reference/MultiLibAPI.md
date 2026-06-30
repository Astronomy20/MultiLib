[← Back to Home](../Home.md)

# `MultiLibAPI`

Package: `net.astronomy.multilib.api`

> Public entry point for mods wanting to interact with MultiLib's pattern system.

A thin, stable facade over [`PatternBuilder`](PatternBuilder.md) and [`PatternRegistry`](PatternRegistry.md). Prefer this class over calling `PatternRegistry` directly from dependent mods — it returns defensive copies and is the intended integration surface.

## Methods

### `pattern()`

```java
public static PatternBuilder pattern()
```

Returns a new `PatternBuilder` (`new PatternBuilder()`). Equivalent to `PatternManager.pattern()` — use whichever reads better in your code; this wiki's examples use `PatternManager.pattern()` since that's also what the codebase's own `ExamplePattern` uses, but both are identical.

```java
MultiLibAPI.pattern()
        .key('X', Blocks.IRON_BLOCK)
        .layer("X")
        .action((level, origin) -> { /* ... */ })
        .build();
```

---

### `getAllPatterns()`

```java
public static List<PatternManager> getAllPatterns()
```

Returns `List.copyOf(PatternRegistry.getAllPatterns())` — an immutable snapshot of every pattern registered by **any** mod (not just yours) at the time of the call. Useful for diagnostics/debug commands, or for mods that want to inspect/visualize all known multiblock structures (e.g. a JEI/REI-style "what can I build" listing).

---

### `getPatternsFor(Block block)`

```java
public static List<PatternManager> getPatternsFor(Block block)
```

Returns every registered pattern (from any mod) that uses `block` as a key. Thin wrapper over `PatternRegistry.getPatternsFor(Block)`.

## When to use this vs. `PatternRegistry` directly

| | `MultiLibAPI` | `PatternRegistry` |
|---|---|---|
| Intended for | Dependent mods (public API) | MultiLib internals |
| Copy safety | Returns immutable copies | `getAllPatterns()` returns the live backing set |
| Stability | Treat as the supported contract | Implementation detail, may change |

## See also

- [PatternBuilder](PatternBuilder.md)
- [PatternRegistry](PatternRegistry.md)
- [Getting Started](../Getting-Started.md)

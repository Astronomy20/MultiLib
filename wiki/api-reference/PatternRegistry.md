[← Back to Home](../Home.md)

# `PatternRegistry`

Package: `net.astronomy.multilib.pattern`

> Manages all pattern registrations and actions.

An internal, process-wide registry mapping each registered [`PatternManager`](PatternManager.md) to its [`PatternAction`](PatternAction.md). Backed by a single static `Map<PatternManager, PatternAction>` (a plain `HashMap`, not thread-confined or synchronized — see [Caveats](#caveats)).

You normally never call this class directly: [`PatternBuilder.build()`](PatternBuilder.md#build) registers for you when an action is set, and MultiLib's internal block-placement listener uses `getPatternsFor` to find candidates. Direct calls are mainly useful for introspection/debugging, or for [`MultiLibAPI`](MultiLibAPI.md), which wraps this class for public consumption.

## Methods

### `register(PatternManager pattern, PatternAction action)`

```java
public static void register(PatternManager pattern, PatternAction action)
```

Adds (or replaces, if the same `PatternManager` instance is registered again) the pattern → action mapping. There is no way to *unregister* a pattern — entries live for the lifetime of the JVM/server process.

---

### `getAllPatterns()`

```java
public static Collection<PatternManager> getAllPatterns()
```

Returns a live view (`PATTERNS.keySet()`) of every currently registered pattern. **Not a defensive copy** — see [Caveats](#caveats). Prefer [`MultiLibAPI.getAllPatterns()`](MultiLibAPI.md#getallpatterns), which returns an immutable copy.

---

### `getPatternsFor(Block block)`

```java
public static List<PatternManager> getPatternsFor(Block block)
```

Returns every registered pattern that uses `block` as one of its keys (via `PatternManager.isKeyBlock`). This is an **O(n) linear scan** over all registered patterns, run once per relevant block placement — fine for a reasonable number of patterns, but worth knowing if your modpack ends up with thousands of registered patterns sharing common key blocks (e.g. `Blocks.STONE`).

## Caveats

- **Not thread-safe.** Registration is expected to happen during mod setup (single-threaded), and lookups happen on the server thread during block placement. Don't register patterns concurrently from multiple threads.
- **No unregistration.** Patterns registered once stay registered for the process lifetime; there's no API to remove a pattern (relevant mainly for datapack/dynamic-reload scenarios, which this registry does not support).
- **`getAllPatterns()` exposes the live key set**, not a copy — mutating it directly (e.g. via iterator `remove()`) would corrupt the registry. Use `MultiLibAPI.getAllPatterns()` for a safe copy.

## See also

- [PatternBuilder](PatternBuilder.md#build) — automatic registration on `.build()`
- [MultiLibAPI](MultiLibAPI.md) — public, copy-safe wrapper around this registry

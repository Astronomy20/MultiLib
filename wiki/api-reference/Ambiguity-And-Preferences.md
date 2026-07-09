[← Back to Home](../index.md)

# Ambiguity & Preferences

The uncommon-but-real case where **the same block type is a valid core or activation symbol for more than one registered definition**. Priority order (see [Core Concepts § Registration and lookup](../Core-Concepts.md#registration-and-lookup)) resolves this globally — but it has no notion of *which* structure a player is building at a specific position. This system lets a per-position override break the tie.

Packages: `net.astronomy.multilib.core.registry` (resolver, tracker), `net.astronomy.multilib.core.preference` (dev wrench), `net.astronomy.multilib.api` (public API).

## When does ambiguity happen?

Every feature that acts on an unformed block — the [ghost overlay](../Advanced-Features.md#ghost-overlay), [auto-place](../Advanced-Features.md#auto-place), wrench formation — must resolve one definition out of the possibly-many that share the clicked block. Most of the time there's only one candidate and there's nothing to decide. Ambiguity arises only when **2+ eligible definitions** list that exact block as their core (or activation) symbol — e.g. two reactor tiers whose controller is the same block.

## Resolution order

`MultiblockAmbiguityResolver.resolve(level, pos, matcher)` is the single shared answer to "which definition should this block be treated as":

1. Take [`MultiblockRegistry.getCandidatesFor(block)`](MultiblockInstance-And-Registry.md) (already priority-sorted).
2. Filter to whatever the caller's predicate accepts (e.g. "core symbol only" for auto-place, "core or activation" for the ghost overlay).
3. **0 or 1 candidate** → nothing to disambiguate; priority order decides, exactly as before.
4. **2+ candidates** → consult the [preference tracker](#preference-persistence). If a stored preference is still among the eligible candidates, it wins. Otherwise (unset, or stale because the block/definition changed) fall through to priority order — the first, highest-priority candidate.

A preference is only ever an **override on top of** priority order, never the sole source of truth. A stale binding is silently ignored, so this is never worse than plain priority resolution — only ever more precise.

`MultiblockAmbiguityResolver.candidatesAt(level, pos, matcher)` returns the full priority-sorted candidate list without touching the tracker — for tools (like the preference wrench) that need to show a player *everything* ambiguous there, not just the winner. It takes a plain `Level`, so client-side picker UI and server-side validation run the identical check and can't drift apart.

## Public API

Three thin, validated passthroughs on [`MultiLib`](MultiLib.md) — the intended integration surface. MultiLib ships **no forced UI**; a mod can drive these from its own tool, GUI, or command.

### `setPreferredDefinition(ServerLevel level, BlockPos pos, ResourceLocation definitionId)`

```java
public static boolean setPreferredDefinition(ServerLevel level, BlockPos pos, ResourceLocation definitionId)
```

Binds `pos` to `definitionId`: when the block there is a valid core/activation symbol for more than one definition, `definitionId` wins at that position instead of whatever priority order would pick.

Validates immediately — `definitionId` must presently be among the candidates the resolver would consider for the block *currently* at `pos` (either core or activation). If not, it's a no-op returning **`false`**. Returns `true` when the binding is accepted and stored.

A binding that goes stale later (block changes, definition removed) is **not** cleaned up eagerly; `resolve(...)` falls back to priority order on its own next time, as if nothing were set.

### `getPreferredDefinition(ServerLevel level, BlockPos pos)`

```java
public static Optional<ResourceLocation> getPreferredDefinition(ServerLevel level, BlockPos pos)
```

The definition id currently bound to `pos`, or empty if never set / already cleared.

### `clearPreferredDefinition(ServerLevel level, BlockPos pos)`

```java
public static void clearPreferredDefinition(ServerLevel level, BlockPos pos)
```

Removes any binding for `pos`. No-op if none was set.

## Preference persistence

`MultiblockPreferenceTracker` is a per-`ServerLevel` [`SavedData`](https://docs.neoforged.net/) named `multilib_preferences`, keyed by `BlockPos`. It's real per-world gameplay state — persisted across restarts, just like [`WorldMultiblockTracker`](MultiblockInstance-And-Registry.md). Corrupted/foreign entries are skipped on load rather than failing the whole file. You normally never touch it directly; go through the `MultiLib` methods above.

## Preference wrench (dev mode)

MultiLib's own optional, dev-facing tool built on the exact same API — the **Multiblock Preference Wrench**. Like the rest of the [Dev Tools](../Dev-Tools.md), it's registered **only when [`devMode`](../Configuration.md#commonconfig-configmultilibcommontoml) is `true`**, appearing in the Tools & Utilities creative tab; a production build carries no trace of it.

Right-clicking a core/activation block with it:

- opens a picker listing every definition that block is ambiguously a candidate for (one button per definition, using the same display-name convention the recipe viewers use, plus Cancel);
- picking one calls `setPreferredDefinition(...)` for that exact position and reports the outcome as a chat line;
- clicking a block that's a core/activation symbol for exactly one definition (no variants to choose between) reports that as a chat line instead of opening a picker;
- clicking a block that isn't a core/activation symbol for any registered definition reports that as a chat line too.

It's an authoring convenience for testing multi-candidate setups; ship your own tool/command/GUI (built on the public API) for anything player-facing.

## See also

- [MultiLib](MultiLib.md) — the three preference methods in the full API listing
- [Core Concepts § Registration and lookup](../Core-Concepts.md#registration-and-lookup) — how priority order works
- [Dev Tools](../Dev-Tools.md) — the other `devMode`-gated authoring aids
- [MultiblockBuilder § priority](MultiblockBuilder.md) — the tie-break priority set per definition

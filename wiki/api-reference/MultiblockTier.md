[← Back to Home](../index.md)

# `MultiblockTier`

Package: `net.astronomy.multilib.api.tier` (`MultiblockTier`, `TierLevel`, `MultiblockTierResolution`), `net.astronomy.multilib.api.definition` (`TierSpec`)

Lets a structure declare, per symbol, a progression of named "tiers" (e.g. a casing block that comes in basic/advanced/elite variants) and resolves which tier is actually present in a formed instance by reading the world. Useful for GregTech-style multiblocks whose performance scales with the casing tier used, without hardcoding a separate definition per tier.

## Declaring tiers

```java
public MultiblockBuilder tier(char symbol, String name, Block... blocks);
public MultiblockBuilder tier(char symbol, String name, TagKey<Block> tag);
```

Declared on [`MultiblockBuilder`](MultiblockBuilder.md#tiers). Call repeatedly for the same symbol from lowest to highest tier - each call's position in that order becomes its `TierSpec#ordinal()` (0 = first/lowest), which is what a caller compares tiers by, not the name itself. The tag-backed overload lets a third-party addon contribute its own blocks to a tier via datapack (adding to the tag), without the multiblock's own definition needing to know about them ahead of time.

## `TierSpec`

```java
public record TierSpec(String name, int ordinal, Set<Block> blocks, @Nullable TagKey<Block> tag, Map<String, Double> stats) {
    public boolean matches(Block block);
}
```

One named tier level declared for a given pattern symbol. Backed by either an explicit block set or a `TagKey` (never both at once in practice, though nothing stops declaring both). Resolved against the actual placed block at query time (see `MultiblockTier` below) rather than cached, since tag membership can change on any `/reload`.

`stats` is an immutable, possibly empty `key → value` map attached at declaration (see [MultiblockBuilder § Tiers](MultiblockBuilder.md#tiers)) - arbitrary numbers like `"speed" → 2.0` that machine logic reads back from the resolved tier instead of comparing tier names. The pre-stats 4-argument constructor still exists and defaults to an empty map, so existing callers are unaffected. Tiers (and therefore stats) are not part of the JSON datapack format.

## `MultiblockTier`

```java
public final class MultiblockTier {
    public static MultiblockTierResolution get(MultiblockContext ctx);
    public static MultiblockTierResolution get(ServerLevel level, MultiblockInstance instance, MultiblockDefinition definition);
}
```

Resolves the tier of each tiered symbol of a formed multiblock against the blocks actually placed in the world right now. Never cached: a `TierSpec` can be backed by a `TagKey`, and tag membership can change on any `/reload`, so there's nothing safe to invalidate - every call just re-reads current world state.

If a symbol occupies multiple positions (e.g. a ring of casings) and those positions hold blocks of different declared tiers, the structure is only as strong as the weakest one placed for that symbol - `get` keeps the lowest-ordinal match found across all of the symbol's positions.

```java
MultiblockTierResolution resolution = MultiblockTier.get(ctx);
resolution.tierForSymbol('C').ifPresent(tier -> { /* ... */ });
```

### `TierLevel`

```java
public record TierLevel(String name, int ordinal, Map<String, Double> stats) {
    public boolean isAtLeast(TierLevel other);
}
```

A resolved tier level for a single pattern symbol: the declared `name`, its declaration-order `ordinal` (0 = lowest), and the declaring `TierSpec`'s stat map carried through resolution. Kept separate from `TierSpec` since a spec is "what block(s) count as this tier" while a `TierLevel` is just the resolved identity/rank a caller compares - no block set or tag baggage needed once a match has already happened. The pre-stats 2-argument constructor still exists (empty stats).

### `MultiblockTierResolution`

```java
public record MultiblockTierResolution(Map<Character, TierLevel> tierBySymbol) {
    public Optional<TierLevel> tierForSymbol(char symbol);
    public Optional<TierLevel> overallTier();
    public Optional<TierLevel> overallTier(BinaryOperator<TierLevel> reducer);
    public Map<String, Double> statsFor(char symbol);
    public double combinedStats(String key, DoubleBinaryOperator merger, double identity);
    public double stat(String key, double fallback);
}
```

Immutable snapshot of the tier resolved for each tiered symbol of a formed multiblock instance, at the moment it was computed. Only holds symbols that actually have a declared tier list **and** a match against the currently placed block - a tiered symbol whose placed block matches none of its declared `TierSpec`s is simply absent here rather than treated as an error.

`overallTier()` reduces across every resolved symbol using the weakest one (minimum ordinal) as the limiting factor - a structure is only as strong as its lowest-tier part. The overloaded `overallTier(BinaryOperator<TierLevel>)` lets you pick a different reduction (e.g. the highest tier present) instead of that default.

The three stat accessors deliberately never guess a merge rule:

- `statsFor(char)` - the raw per-symbol map of the resolved tier, no merging (empty map if the symbol resolved no tier or declared no stats).
- `combinedStats(key, merger, identity)` - folds `key` across every resolved symbol with **your** operator, starting from `identity` and skipping symbols that don't declare the key (`Double::sum` with `0.0`, `Math::min` with `Double.POSITIVE_INFINITY`, etc.).
- `stat(key, fallback)` - the simple accessor for the common case where exactly one symbol owns a key: returns `fallback` if no resolved symbol declares it, and **throws `IllegalStateException` if more than one does** - that ambiguity is a definition bug to surface, not something to silently resolve with an arbitrary winner.

## See also

- [MultiblockBuilder § Tiers](MultiblockBuilder.md#tiers)
- [MultiblockComposition](MultiblockComposition.md) - reports raw block counts rather than resolved tier names.
- [Callbacks & Events](Callbacks-And-Events.md)

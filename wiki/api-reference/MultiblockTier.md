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
public record TierSpec(String name, int ordinal, Set<Block> blocks, @Nullable TagKey<Block> tag) {
    public boolean matches(Block block);
}
```

One named tier level declared for a given pattern symbol. Backed by either an explicit block set or a `TagKey` (never both at once in practice, though nothing stops declaring both). Resolved against the actual placed block at query time (see `MultiblockTier` below) rather than cached, since tag membership can change on any `/reload`.

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
public record TierLevel(String name, int ordinal) {
    public boolean isAtLeast(TierLevel other);
}
```

A resolved tier level for a single pattern symbol: the declared `name` and its declaration-order `ordinal` (0 = lowest). Kept separate from `TierSpec` since a spec is "what block(s) count as this tier" while a `TierLevel` is just the resolved identity/rank a caller compares - no block set or tag baggage needed once a match has already happened.

### `MultiblockTierResolution`

```java
public record MultiblockTierResolution(Map<Character, TierLevel> tierBySymbol) {
    public Optional<TierLevel> tierForSymbol(char symbol);
    public Optional<TierLevel> overallTier();
    public Optional<TierLevel> overallTier(BinaryOperator<TierLevel> reducer);
}
```

Immutable snapshot of the tier resolved for each tiered symbol of a formed multiblock instance, at the moment it was computed. Only holds symbols that actually have a declared tier list **and** a match against the currently placed block - a tiered symbol whose placed block matches none of its declared `TierSpec`s is simply absent here rather than treated as an error.

`overallTier()` reduces across every resolved symbol using the weakest one (minimum ordinal) as the limiting factor - a structure is only as strong as its lowest-tier part. The overloaded `overallTier(BinaryOperator<TierLevel>)` lets you pick a different reduction (e.g. the highest tier present) instead of that default.

## See also

- [MultiblockBuilder § Tiers](MultiblockBuilder.md#tiers)
- [MultiblockComposition](MultiblockComposition.md) - reports raw block counts rather than resolved tier names.
- [Callbacks & Events](Callbacks-And-Events.md)

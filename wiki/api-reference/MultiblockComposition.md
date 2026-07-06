[← Back to Home](../index.md)

# `MultiblockComposition`

Package: `net.astronomy.multilib.api.composition`

Reports what a formed multiblock structure is actually built out of, so a consuming mod can show its own "bill of materials" (a breakdown of which blocks make up the structure and how many of each) without reimplementing structure introspection. Read-only - this never places, breaks, or otherwise changes anything.

Unlike [`MultiblockProgressAPI`](Multiblock-States-And-Progress.md#multiblockprogressapi), this operates on an already-formed `MultiblockInstance` rather than a bare core position: there's no pattern matching or orientation detection to do, since the instance already tracks which world position belongs to which pattern symbol.

## `MultiblockComposition`

```java
public final class MultiblockComposition {
    public static CompositionResult compute(MultiblockContext ctx);
    public static CompositionResult compute(ServerLevel level, MultiblockInstance instance);
}
```

`compute(ctx)` is a convenience overload pulling the level and instance out of a `MultiblockContext` (the same context passed to `onFormed`/`onBroken`/tick callbacks). The other overload reads, for every pattern symbol declared on the instance's resolved definition, the block state currently sitting at each of the instance's tracked positions for that symbol.

```java
CompositionResult result = MultiblockComposition.compute(ctx);
Map<Block, Integer> counts = result.countByBlock();
```

## `CompositionResult`

```java
public final class CompositionResult {
    public Map<Character, List<BlockIngredientMatch>> matchesBySymbol();
    public Map<Block, Integer> countByBlock();
    public Map<Block, Integer> countForSymbol(char symbol);
    public int totalCount();
    public List<BlockIngredientMatch> ingredientMatch();
}
```

Immutable introspection result - what's actually sitting at every tracked position, grouped by pattern symbol.

- `countByBlock()` / `countForSymbol(char)` - the default, aggregated view most callers want (e.g. "how many iron blocks make up this structure"). `countForSymbol` restricts the count to a single pattern symbol's positions.
- `totalCount()` - total number of tracked positions across every symbol.
- `ingredientMatch()` - the escape hatch down to raw per-position data, flattened across every symbol, for callers that need more than a count (e.g. highlighting specific positions).

## `BlockIngredientMatch`

```java
public record BlockIngredientMatch(BlockPos pos, char symbol, BlockState actualState) {}
```

The raw match for a single position of an already-formed structure - which symbol occupies `pos` in the definition's pattern, and what block is actually sitting there in the world. This is the per-position granularity `CompositionResult` aggregates on top of.

## See also

- [Multiblock States & Progress Tracking § MultiblockProgressAPI](Multiblock-States-And-Progress.md#multiblockprogressapi) - the sibling read-only API for **in-progress** (not-yet-formed) structures.
- [MultiblockAbility](MultiblockAbility.md) - a complementary way to ask "which parts fulfill role X" instead of "what block is at position Y".
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md)
- [Callbacks & Events](Callbacks-And-Events.md)

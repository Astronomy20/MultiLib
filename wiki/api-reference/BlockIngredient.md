[← Back to Home](../index.md)

# `BlockIngredient`

Package: `net.astronomy.multilib.api.ingredient`

Interface describing what a pattern symbol matches against in the world. Replaces the old API's fixed "symbol → exactly one `Block`" mapping with a pluggable abstraction.

## Interface

```java
public interface BlockIngredient {
    boolean matches(BlockState state);
    Set<Block> getCandidateBlocks();
    default BlockState getRenderState() { ... }
}
```

- `matches(BlockState)` - the actual match test used by every matcher.
- `getCandidateBlocks()` - concrete blocks this ingredient could match, if enumerable. Used by `MultiblockRegistry` to index definitions by block (an ingredient that returns an empty set, like a tag or predicate, makes its definition "always checked" against every block placement instead of being indexed - see [Core Concepts § Registration and lookup](../Core-Concepts.md#registration-and-lookup)).
- `getRenderState()` - the `BlockState` previews (JEI/REI/EMI 3D model, ghost overlay) should render for this ingredient. Defaults to the first candidate block's default state, or **`null`** if `getCandidateBlocks()` is empty (tags, predicates, `any()`) - callers must null-check.

## Factory methods

### `BlockIngredient.of(Block block)`
Matches exactly one block (`SingleBlockIngredient`). The common case; also what `.key(char, Block)` wraps automatically.

### `BlockIngredient.ofState(Block block)`
Returns a `StatePropertyIngredient.Builder`:
```java
BlockIngredient.ofState(Blocks.FURNACE)
        .require(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        .build()
```
Matches one block **and** specific blockstate properties. `getRenderState()` applies every required property on top of the block's default state, so directional blocks preview correctly oriented.

### `BlockIngredient.tag(TagKey<Block> tag)`
Matches any block in `tag`. `getCandidateBlocks()` returns an empty set (tags aren't eagerly enumerated), so definitions using only tag ingredients for their activation/core symbol are checked against every block placement rather than indexed.

### `BlockIngredient.anyOf(BlockIngredient... ingredients)`
Matches if any of the given ingredients matches. `getCandidateBlocks()` is the union of the children's candidates.

### `BlockIngredient.predicate(Predicate<BlockState> predicate)`
Arbitrary logic. `getCandidateBlocks()` returns an empty set - same indexing caveat as tags.

### `BlockIngredient.any()`
Matches every `BlockState` unconditionally (including air). `getCandidateBlocks()` is empty.

## Choosing an ingredient type

| Need | Use |
|---|---|
| One exact block | `of(block)` |
| One block, one specific facing/property | `ofState(block).require(...).build()` |
| Any block from a set you maintain via a tag | `tag(tagKey)` |
| "Any of these 3 specific blocks" | `anyOf(of(a), of(b), of(c))` |
| Logic that can't be expressed declaratively | `predicate(state -> ...)` |
| A cell that accepts anything (including air) | `any()` |

## `IWallSharable`

```java
public interface IWallSharable {
    WallSharingMode getDefaultWallSharingMode();
}
```

An optional interface a `Block` class can implement to declare its own default wall-sharing behavior. It's only consulted as part of the ordinary-symbol wall-sharing priority chain (see [`MultiblockDefinition#getWallSharingMode`](MultiblockDefinition.md)), and only when a symbol's ingredient has **exactly one** candidate block - with multiple candidate blocks there's no single block to ask. No block in this codebase implements it by default; it's an extension point for your own `Block` subclasses. See [Advanced Features § Wall sharing](../Advanced-Features.md#wall-sharing) for the full chain.

## Performance note

Definitions whose **activation or core symbol** ingredient returns an empty `getCandidateBlocks()` (tags, predicates, `any()`) fall back to being checked on *every* block placement in the world, not just placements of a specific block - see `MultiblockRegistry.getCandidatesFor` and the "always-checked" list in [Core Concepts](../Core-Concepts.md#registration-and-lookup). Prefer `of(...)`/`ofState(...)`/`anyOf(...)` of enumerable blocks for activation/core symbols where possible; reserve tags/predicates for body symbols, where this cost doesn't apply.

## See also

- [MultiblockBuilder § Symbols](MultiblockBuilder.md#symbols-core-activation-priority)
- [Core Concepts](../Core-Concepts.md#symbols-and-blockingredient)
- [Advanced Features § JSON/datapack definitions](../Advanced-Features.md#jsondatapack-definitions) - the JSON schema for each ingredient type

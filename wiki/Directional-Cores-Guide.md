[← Back to Home](index.md)

# Directional Cores Guide

For a core with its own placed facing (like a furnace): make the ghost overlay and auto-place preview follow *that* facing instead of the player's look direction. The switch is `BlockDefinitionBuilder.mainFace()`.

## Why

By default the preview orientation follows the clicked face or player look direction ([ghost overlay](Advanced-Features.md#ghost-overlay)) — correct for a generic core. But if the core is directional (a `HORIZONTAL_FACING`/`FACING` property), you want the rest of the structure to preview relative to how the core was placed, not where the player stands. `.mainFace()` does that.

## 1. Give the core a facing

A normal directional block — nothing MultiLib-specific:

```java
public class MyDirectionalControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }
    // openMenu / newBlockEntity / getTicker as usual
}
```

This is `ExampleDirectionalControllerBlock` in the source. `.mainFace()` is independent of the controller abstractions but composes with them.

## 2. Declare `.mainFace()`

```java
MultiLib.block(MyBlocks.DIRECTIONAL_CONTROLLER).mainFace().build();
```

A block-level declaration ([`BlockDefinition`](api-reference/BlockDefinition.md)). It takes effect only if the block has `HORIZONTAL_FACING` or `FACING` (`extractMainFace` checks horizontal first, then projects a vertical `FACING` onto the nearest horizontal). On a block with neither, it's a silent no-op — the preview just follows the player.

## 3. Design the pattern normally

Nothing special; keep whatever rotation policy fits:

```java
MultiLib.define(id)
        .layer(" G ", "IOD", " E ")
        .key('O', BlockIngredient.of(MyBlocks.DIRECTIONAL_CONTROLLER))
        // G/D/E/I keys...
        .core('O')
        .rotations(RotationMode.HORIZONTAL)
        .build();

MultiLib.block(MyBlocks.DIRECTIONAL_CONTROLLER).mainFace().build();
```

This is `ExampleDirectionalPattern`, asymmetric on all four sides so the preview orientation is obvious when testing. The key point: **"the matcher accepts any rotation" and "the preview shows one fixed orientation" are independent.** `RotationMode.HORIZONTAL` still lets the structure be built facing any direction; `.mainFace()` only changes what the preview shows before it's built.

## Summary

| | No `.mainFace()` | `.mainFace()` set |
|---|---|---|
| Preview orientation | Clicked face / player look | The core's actual facing |
| Matcher's accepted orientations | `RotationMode`/`allowRotation` | Unchanged |

## See also

- [Advanced Features § Ghost overlay](Advanced-Features.md#ghost-overlay), [`BlockDefinition`](api-reference/BlockDefinition.md), [Rotation & Matching](Rotation-And-Matching.md)

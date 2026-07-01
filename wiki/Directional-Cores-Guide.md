[← Back to Home](Home.md)

# Directional Cores Guide

How to build a structure whose core has its own meaningful placed facing — like a furnace — so the ghost overlay and auto-place preview always show the structure in the direction the core actually faces, instead of following the player's look direction. This is a fixed-facing core structure, something the old API couldn't express cleanly.

## The problem this solves

By default, MultiLib's ghost overlay/auto-place preview orientation is derived from either the face the player clicked or the player's look direction (see [Advanced Features § Ghost overlay](Advanced-Features.md#ghost-overlay)). That's the right behavior for a core with no facing of its own — any generic block placed as the controller. But if your core block **is** directional (a furnace-like block with its own `HORIZONTAL_FACING` or `FACING` blockstate property), you almost always want the preview to respect *that* facing instead: the player placed the core a specific way, and the rest of the structure should preview relative to it, not relative to wherever they happen to be standing when they open the overlay.

`BlockDefinitionBuilder.mainFace()` is exactly this switch.

## Step 1: give the core block its own facing

Nothing MultiLib-specific here — a normal directional `Block`, the same way you'd write a furnace:

```java
public class MyDirectionalControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    public MyDirectionalControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(AbstractMultiblockPartBlock.MODEL_HIDDEN, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

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

    // ... openMenu(...), newBlockEntity(...), getTicker(...) as usual — see Block Entity Abstractions
}
```

This is `ExampleDirectionalControllerBlock` in the source tree, verbatim in structure. Note it still extends `AbstractMultiblockControllerBlock` — `.mainFace()` is independent of whether you use the controller block-entity abstractions, but they compose cleanly.

## Step 2: declare `.mainFace()` on the block

```java
MultiLibAPI.block(MyBlocks.DIRECTIONAL_CONTROLLER).mainFace().build();
```

This is a separate, block-level declaration (`BlockDefinition`, not `MultiblockDefinition`) — see the [`BlockDefinition` reference](api-reference/BlockDefinition.md). It only takes effect if the block actually has a `HORIZONTAL_FACING` or `FACING` blockstate property (`OverlayRequestHandler.extractMainFace` checks for `HORIZONTAL_FACING` first, then falls back to the full 6-way `FACING`, projecting a vertical facing onto the nearest horizontal direction since the overlay system only reasons in horizontal yaw steps). Declaring `.mainFace()` on a block with neither property is a silent no-op — the preview falls back to following the player as if `.mainFace()` had never been called.

## Step 3: design the pattern normally

The pattern itself doesn't need anything special — build it exactly as any other shaped structure, keeping `RotationMode.HORIZONTAL` (or whatever rotation policy fits) so the matcher still accepts the structure built facing any of the 4 directions:

```java
MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("examplemod", "directional_altar"))
        .name("directional_altar")
        .layer(
                " G ",
                "IOD",
                " E "
        )
        .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
        .key('D', BlockIngredient.of(Blocks.DIAMOND_BLOCK))
        .key('E', BlockIngredient.of(Blocks.EMERALD_BLOCK))
        .key('I', BlockIngredient.of(Blocks.IRON_BLOCK))
        .key('O', BlockIngredient.of(MyBlocks.DIRECTIONAL_CONTROLLER))
        .core('O')
        .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
        .rotations(RotationMode.HORIZONTAL)
        .build();

MultiLibAPI.block(MyBlocks.DIRECTIONAL_CONTROLLER).mainFace().build();
```

This is `ExampleDirectionalPattern` from the source tree — deliberately asymmetric on all 4 horizontal sides (gold north, diamond east, emerald south, iron west of the core) so the preview's orientation is visually obvious when testing. The key point this example demonstrates: **"the matcher accepts any rotation" and "the preview always shows one fixed orientation" are independent settings.** `RotationMode.HORIZONTAL` still lets the structure be built facing any of the 4 directions — `.mainFace()` only affects what the *ghost overlay/auto-place preview* shows before the structure is built, always reflecting the core's actual placed facing rather than cycling with the player's look direction the way a non-directional core's preview does.

## What changes in practice

| | Non-directional core (no `.mainFace()`) | Directional core (`.mainFace()` set) |
|---|---|---|
| Ghost overlay orientation on fresh activation | Follows the clicked face / player look direction | Always the core's actual `HORIZONTAL_FACING`/`FACING` value |
| Auto-place preview | Same as overlay | Same as overlay |
| Matcher's accepted orientations | Governed entirely by `RotationMode`/`allowRotation(...)` | Unchanged — still governed by `RotationMode`/`allowRotation(...)` |

## See also

- [Advanced Features § Ghost overlay, Auto-place](Advanced-Features.md#ghost-overlay)
- [`BlockDefinition` reference](api-reference/BlockDefinition.md)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md)

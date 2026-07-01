---
navigation:
  title: Example Blocks
  parent: index.md
  position: 1
item_ids:
  - multilib:example_part
  - multilib:example_controller
  - multilib:example_directional_controller
  - multilib:wrench
---

# Example Blocks

MultiLib ships a handful of placeholder blocks and one item purely to demonstrate and test the
library's multiblock features. None of them are meant to be used in a real mod — copy the pattern,
not the blocks.

## Example Multiblock Part

<Row>
  <BlockImage id="multilib:example_part" />
</Row>

<ItemLink id="multilib:example_part" /> is a plain filler block used as the body of the
[Example Multiblock](example_multiblock.md) pattern. It carries no special behavior on its own —
the multiblock definition is what gives the assembled structure meaning.

## Example Controller

<Row>
  <BlockImage id="multilib:example_controller" />
</Row>

<ItemLink id="multilib:example_controller" /> is the **core** block of the example multiblock.
Placing it (or right-clicking it with the <ItemLink id="multilib:wrench" />, see below) while the
rest of the pattern is already assembled triggers formation.

## Example Directional Controller

<Row>
  <BlockImage id="multilib:example_directional_controller" />
</Row>

<ItemLink id="multilib:example_directional_controller" /> behaves like the controller above, but
remembers the direction it was placed in (like a furnace). It is the core of the
`multilib:example_directional` pattern, which uses an intentionally asymmetric layout (gold to the
north, diamond to the east, emerald to the south, iron to the west) so the ghost-overlay preview's
orientation is unambiguous.

## Multiblock Wrench

<Row>
  <ItemImage id="multilib:wrench" />
</Row>

<ItemLink id="multilib:wrench" /> is a reference tool implementation for triggering multiblock
formation by right-clicking a placed core block. MultiLib itself does not require this specific
item — any item implementing `IMultiblockWrench` can be used the same way.

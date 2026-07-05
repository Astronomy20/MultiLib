---
navigation:
  title: MultiLib
---

# MultiLib Example Guide

MultiLib is a library mod for building **multiblock structures**: patterns of blocks that,
once assembled correctly, are recognized as a single functional structure with a controller
block at their core.

This guide walks through the example content that ships with MultiLib itself, so mod authors
can see the library's features in action before defining their own multiblocks.

- [Example Blocks](blocks.md) - the placeholder blocks used to build the example structures.
- [Example Multiblock](example_multiblock.md) - a Java-defined multiblock (`multilib:example`)
  showing a 2-layer pattern, automatic/wrench formation, and an on-formed effect.
- [Datapack Multiblock](datapack_multiblock.md) - the same kind of structure defined purely
  through a datapack JSON file (`example_simple`), with no Java code at all.

<GameScene zoom="4">
  <Block id="multilib:example_controller" />
  <Block id="multilib:example_part" x="1" />
  <Block id="multilib:example_part" x="-1" />
  <Block id="multilib:example_part" z="1" />
  <Block id="minecraft:gold_block" z="-1" />
</GameScene>

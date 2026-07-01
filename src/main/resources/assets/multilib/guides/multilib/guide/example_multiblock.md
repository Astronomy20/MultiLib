---
navigation:
  title: Example Multiblock
  parent: index.md
  position: 2
item_ids:
  - multilib:example_controller
---

# Example Multiblock

`multilib:example` (registered in `ExamplePattern.java` as the definition named
`example_multiblock`) is a small 2-layer, 3x3 structure that demonstrates a Java-defined
multiblock with automatic and wrench-triggered formation, horizontal rotation support, and an
on-formed/on-broken effect.

## Layout

`MultiblockBuilder.layer(...)` is called twice; per its own javadoc, **the first call is the top
layer and each subsequent call stacks one level lower** (the last call is the bottom). So despite
being written first in the source, `"PPP"," P "," G "` is the **top** layer, and `"POP"," P "," G "`
(with the core) is the **bottom** layer:

```
Layer 2 (top):            Layer 1 (bottom, core layer):
P P P                      P O P
_ P _                      _ P _
_ G _                      _ G _
```

Within a layer, each row string maps directly to Z (row 0 = north-most) and each character position
in the row maps directly to X (column 0 = west-most) — confirmed against `ShapedMatcher`'s
`relZ = row - centerZ` / `relX = col - centerX` matching logic, with no flips.

| Symbol | Block                                | Role                                    |
|--------|---------------------------------------|------------------------------------------|
| `P`    | `multilib:example_part`               | Filler / body block                      |
| `O`    | `multilib:example_controller`         | Core — placing this triggers formation   |
| `G`    | `minecraft:gold_block`                | Body block, kept visible after formation |
| `_`    | *(air, no block required)*            | Empty slot in the pattern                |

The core (`O`) sits in the center of the **bottom** layer. Formation mode is
`AUTOMATIC_AND_WRENCH`: the structure forms as soon as the core is placed with the rest of the
pattern already in place, or when right-clicked afterwards with a
<ItemLink id="multilib:wrench" /> (see [Example Blocks](blocks.md)). The structure also accepts
horizontal rotation (0/90/180/270 degrees) when matching.

## 3D Preview

<GameScene zoom="5" interactive={true}>
  <Block id="multilib:example_part" />
  <Block id="multilib:example_controller" x="1" />
  <Block id="multilib:example_part" x="2" />
  <Block id="multilib:example_part" x="1" z="1" />
  <Block id="minecraft:gold_block" x="1" z="2" />

  <Block id="multilib:example_part" y="1" />
  <Block id="multilib:example_part" x="1" y="1" />
  <Block id="multilib:example_part" x="2" y="1" />
  <Block id="multilib:example_part" x="1" y="1" z="1" />
  <Block id="minecraft:gold_block" x="1" y="1" z="2" />

  <BlockAnnotation x="1" y="0" z="0" color="#ffcc00">
    Core block — <ItemLink id="multilib:example_controller" />. Placing it (or using the wrench
    on it) triggers formation once the rest of the pattern is in place.
  </BlockAnnotation>
</GameScene>

## Formation Effects

- **On formed**: summons a lightning bolt at the structure's origin (the core position).
- **On broken**: broadcasts a chat message to nearby players (within 30 blocks) and plays a glass
  break sound.

See also the [Datapack Multiblock](datapack_multiblock.md) page for a structure defined without
any Java code.

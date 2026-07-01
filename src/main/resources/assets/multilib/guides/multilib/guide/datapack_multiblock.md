---
navigation:
  title: Datapack Multiblock
  parent: index.md
  position: 3
---

# Datapack Multiblock

`multilib:example_simple` is defined entirely through a datapack JSON file at
`data/multilib/multiblocks/example_simple.json` — no Java code is involved. This shows that
mod packs and datapacks can add their own multiblocks to MultiLib without compiling a mod.

## Layout

The `"layers"` JSON array is read in the same top-to-bottom order as `MultiblockBuilder.layer(...)`
calls: the **first** array entry is the **top** layer, and the **last** entry is the **bottom**
layer. So the five layers, from top to bottom, are:

```
Layer 5 (top)      Layer 4            Layer 3            Layer 2 (core)     Layer 1 (bottom)
D D D              D D D              F F F              G G G              H H H
D _ D              D E D              D D D              D D D              D D D
D D D              D D D              A A A              B B B              C C C
```

(Read top-to-bottom in the table above; note this is the reverse of the JSON array's declared
order, and the reverse of the file's own internal "Layer 2"/"Layer 3" comments-as-labels, which
number layers in JSON/declaration order, not world Y order.)

| Symbol | Block                       |
|--------|-------------------------------|
| `D`    | `minecraft:diamond_block`     |
| `E`    | `minecraft:emerald_block`     |
| `A`    | `minecraft:coal_block`        |
| `B`    | `minecraft:slime_block`       |
| `C`    | `minecraft:oak_log`           |
| `F`    | `minecraft:amethyst_block`    |
| `G`    | `minecraft:pink_wool`         |
| `H`    | `minecraft:iron_block`        |
| `_`    | *(air, no block required)*    |

The core is `E` (`minecraft:emerald_block`), located in the center of the JSON's 2nd declared
layer, which is one level below the very top of the structure (world Y = 3 of 0-4 here). The
structure supports horizontal rotation and both automatic placement and an automatic-placement
ghost overlay (`auto_place` / `auto_place_overlay`). On formation, it plays the
`minecraft:block.glass.break` sound.

## 3D Preview (bottom two layers)

<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:iron_block" />
  <Block id="minecraft:iron_block" x="1" />
  <Block id="minecraft:iron_block" x="2" />
  <Block id="minecraft:diamond_block" z="1" />
  <Block id="minecraft:diamond_block" x="1" z="1" />
  <Block id="minecraft:diamond_block" x="2" z="1" />
  <Block id="minecraft:oak_log" z="2" />
  <Block id="minecraft:oak_log" x="1" z="2" />
  <Block id="minecraft:oak_log" x="2" z="2" />

  <Block id="minecraft:pink_wool" y="1" />
  <Block id="minecraft:pink_wool" x="1" y="1" />
  <Block id="minecraft:pink_wool" x="2" y="1" />
  <Block id="minecraft:diamond_block" y="1" z="1" />
  <Block id="minecraft:diamond_block" x="1" y="1" z="1" />
  <Block id="minecraft:diamond_block" x="2" y="1" z="1" />
  <Block id="minecraft:slime_block" y="1" z="2" />
  <Block id="minecraft:slime_block" x="1" y="1" z="2" />
  <Block id="minecraft:slime_block" x="2" y="1" z="2" />

  <BlockAnnotation x="1" y="0" z="1" color="#33cc66">
    Bottom-most layer shown here — `minecraft:iron_block`/`minecraft:diamond_block`/`minecraft:oak_log`.
    The actual core (`minecraft:emerald_block`) is two levels higher and not shown in this
    partial preview; see the table above for the full 5-layer pattern.
  </BlockAnnotation>
</GameScene>

*(Only the bottom two of five layers are shown in the interactive preview for brevity; see the
table above for the full pattern, including the core layer.)*

# MultiLib

**MultiLib** is a library mod that lets other mods define and detect multiblock structures - a fixed 3D arrangement of blocks - without writing their own block-by-block matching, state tracking, or recipe-browser integration.

You describe a structure once with a fluent builder, and MultiLib handles detection (in every orientation it allows), persistent instance tracking, a ghost-overlay preview, an auto-place tool, and JEI/REI/EMI/Patchouli integration.

> **Status:** MultiLib is under active development (`v0.0.1`). It's usable today but expect bugs.

## Why MultiLib

Building a multiblock structure by hand usually means reimplementing the same handful of problems for every mod: scanning neighboring blocks in the right order, handling rotations, persisting which structures are currently formed across a world reload and wiring up a recipe-browser page so players can see the layout. MultiLib solves all of that once, behind a small public API, so mod authors can focus on what their structure *does* rather than how it's detected.

## Features

- **Fluent builder API** - describe a structure as a stack of text-based layers with symbol keys, no manual coordinate math.
- **Flexible matching** - shaped, shapeless, and procedural (`PatternProvider`) structures; shell/interior matching; free blocks.
- **Rotation-aware detection** - horizontal or full 3D rotation modes, plus fixed-facing "directional core" structures.
- **Stateful controller blocks** - optional base classes for a block-entity-backed core that tracks formed/unformed state and ticks while active.
- **Callbacks** - `onFormed` / `onBroken` / tick callbacks fire exactly when they should.
- **Ghost-overlay preview & auto-place** - players can preview a structure in the world before placing it and place the remaining blocks with a single tool.
- **JSON/datapack definitions** - structures can also be defined data-driven.
- **Recipe-browser integration** - JEI, REI, EMI, Patchouli and GuideME support out of the box.

## Quick example

```java
MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("examplemod", "my_altar"))
        .name("my_altar")
        .layer("PPP", " P ", " G ")
        .layer("POP", " P ", " G ")
        .key('P', BlockIngredient.of(Blocks.STONE_BRICKS))
        .key('O', BlockIngredient.of(MyBlocks.CONTROLLER_BLOCK))
        .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
        .core('O')
        .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
        .rotations(RotationMode.HORIZONTAL)
        .onFormed(ctx -> {
            // runs once the structure is detected in the world
        })
        .build();
```

Registered once during mod setup, this is enough for MultiLib to detect the structure in every allowed rotation whenever the core block is placed, track it persistently, and preview/auto-place it for players.

See the [Getting Started guide](wiki/Getting-Started) for the full walkthrough, including the mod-entry-point wiring.

## Documentation

The full API reference and guides live in the [wiki](wiki/Home):

- [Getting Started](wiki/Getting-Started) - add MultiLib as a dependency and register your first multiblock.
- [Core Concepts](wiki/Core-Concepts) - definitions, symbols, ingredients, the coordinate system, formation modes.
- [Pattern Design Guide](wiki/Pattern-Design-Guide) - laying out structures correctly and avoiding common mistakes.
- [Rotation & Matching Deep Dive](wiki/Rotation-And-Matching) - how the matchers actually work.
- [Advanced Features](wiki/Advanced-Features) - shapeless structures, JSON definitions, ghost overlay, auto-place, JEI/REI/EMI/Patchouli/FTB Quests.
- [API Reference](/wiki/Home#7-api-reference) - full method-by-method reference, including [multiblock states & progress tracking](../../wiki/api-reference/Multiblock-States-And-Progress).
- [FAQ & Troubleshooting](wiki/FAQ-Troubleshooting)

## Compatibility

Are available integrations for JEI, REI, EMI, Patchouli, GuideME and FTB Quests - see [Advanced Features](../../wiki/Advanced-Features#jei--rei--emi--patchouli--guideme--ftb-quests-compatibility).

## Contributing

Issues and pull requests are welcome. If you're proposing a new API shape or any other feature, please open an issue to discuss it.

## License

MIT - see the license file for details.

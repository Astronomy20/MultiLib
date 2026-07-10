# MultiLib

**MultiLib** lets other mods define and detect multiblock structures — fixed 3D arrangements of blocks — without writing their own matching, state tracking, or recipe-browser integration. Describe a structure once with a fluent builder; MultiLib handles detection in every allowed orientation, persistent instance tracking, a ghost-overlay preview, an auto-place tool, and JEI/REI/EMI/Patchouli integration.

> **Status:** active development (`v0.0.1`). Usable today, but expect bugs.
>
> Also published as a browsable site at **[astronomy20.github.io/MultiLib](https://astronomy20.github.io/MultiLib/)**.

## Features

- **Fluent builder** — text-based layers with symbol keys, no coordinate math.
- **Flexible matching** — shaped, shapeless, and procedural (`PatternProvider`) structures; shell/interior; free blocks; [pattern variants](api-reference/MultiblockBuilder.md#variants).
- **Rotation-aware** — horizontal or full 3D modes, plus fixed-facing directional cores.
- **Stateful controllers** — base classes for a block-entity core that tracks state and ticks while formed.
- **Callbacks** — `onFormed`/`onBroken`/tick, fired on a real tracked lifecycle.
- **Preview & auto-place** — ghost overlay and one-tool completion.
- **Data-driven** — JSON/datapack definitions and KubeJS scripting.
- **Recipe browsers** — JEI, REI, EMI, Patchouli, GuideME, FTB Quests.
- **Machine toolkit** — energy/fluid/item buffer components, port/hatch base classes, a process engine, redstone/comparator/ownership helpers, `/multilib` commands, tier stat maps, and Jade/The One Probe hover-info. All opt-in and mechanism-only.
- **Ambiguity handling** — a per-position [preferred-definition](api-reference/Ambiguity-And-Preferences.md) override when one block is a valid core for several structures.
- **Configurable** — [runtime config](Configuration.md) for preview/auto-place feel, recipe-browser icon, and the dev-tool suite.

## Quick example

```java
MultiLib.define(ResourceLocation.fromNamespaceAndPath("examplemod", "my_altar"))
        .layer("PPP", " P ", " G ")
        .layer("POP", " P ", " G ")
        .key('P', BlockIngredient.of(Blocks.STONE_BRICKS))
        .key('O', BlockIngredient.of(MyBlocks.CONTROLLER_BLOCK))
        .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
        .core('O')
        .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
        .rotations(RotationMode.HORIZONTAL)
        .onFormed(ctx -> { /* runs once detected */ })
        .build();
```

Registered once at mod setup, this detects the structure in every allowed rotation whenever the core is placed, tracks it persistently, and previews/auto-places it for players.

## Where to start

- **New here?** [Getting Started](Getting-Started.md), then [Core Concepts](Core-Concepts.md).
- **Designing a structure?** [Pattern Design Guide](Pattern-Design-Guide.md) and [Advanced Features](Advanced-Features.md).
- **Full API:** [API Reference](api-reference/MultiLib.md).
- **Tuning behavior?** [Configuration](Configuration.md).
- **Authoring in-game?** [Dev Tools](Dev-Tools.md).
- **Stuck?** [FAQ & Troubleshooting](FAQ-Troubleshooting.md).

## Contributing

Issues and PRs welcome. For a new API shape or feature, please open an issue to discuss first.

## License

MIT — see the license file.

# MultiLib Wiki

MultiLib is a NeoForge 1.21.1 (Java 21) **library mod** that lets other mods define and detect multiblock structures (a fixed 3D arrangement of blocks) without writing their own block-by-block matching, state tracking, or UI integration. You describe a structure with a fluent builder (`MultiLibAPI.define(...)`), attach callbacks for formation/breaking/ticking, and MultiLib handles detection, instance tracking, persistence, a ghost-overlay preview, and recipe-browser (JEI/REI/EMI) integration.

> **Status note:** this wiki documents the API as it exists today in the codebase (`MultiblockDefinition` / `MultiblockBuilder` / `BlockIngredient` / callbacks / block-entity abstractions). MultiLib went through a full rewrite — if you used an older version built around `PatternBuilder`/`PatternManager`/`PatternAction`, that API no longer exists; see [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md) for what changed.

## Table of contents

1. [Getting Started](Getting-Started.md) — adding MultiLib as a dependency and registering your first multiblock
2. [Core Concepts](Core-Concepts.md) — definitions, symbols, ingredients, the coordinate system, formation modes, the activation/tracking flow
3. [Pattern Design Guide](Pattern-Design-Guide.md) — laying out layers/symbols correctly, geometry constraints, common mistakes
4. [Rotation & Matching Deep Dive](Rotation-And-Matching.md) — `RotationMode` vs. granular `allowRotation(...)`, how the shaped/shapeless/functional matchers work
5. [Directional Cores Guide](Directional-Cores-Guide.md) — building a structure with a fixed-facing core (`mainFace()`), a rotatable structure whose preview still respects the core's own orientation
6. [Advanced Features](Advanced-Features.md) — shapeless structures, free blocks, shell/interior matching, procedural `PatternProvider`s, JSON/datapack definitions, ghost overlay, auto-place, wrench tool, JEI/REI/EMI/Patchouli/GuideME integration
7. <a id="7-api-reference"></a>API Reference
   - [MultiLibAPI](api-reference/MultiLibAPI.md)
   - [MultiblockBuilder](api-reference/MultiblockBuilder.md)
   - [MultiblockDefinition](api-reference/MultiblockDefinition.md)
   - [BlockIngredient](api-reference/BlockIngredient.md)
   - [Callbacks & Events](api-reference/Callbacks-And-Events.md)
   - [MultiblockInstance & Registry](api-reference/MultiblockInstance-And-Registry.md)
   - [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md)
   - [BlockDefinition (block-level metadata)](api-reference/BlockDefinition.md)
   - [RotationUtils](api-reference/RotationUtils.md)
8. [FAQ & Troubleshooting](FAQ-Troubleshooting.md)
9. [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md)

## Quick orientation

| If you want to... | Read |
|---|---|
| Add MultiLib to your mod and register a multiblock in 5 minutes | [Getting Started](Getting-Started.md) |
| Understand what a "definition", "symbol", "core" and "activation" mean in MultiLib | [Core Concepts](Core-Concepts.md) |
| Look up a specific method/class signature | [API Reference](#7-api-reference) |
| Understand exactly how matching/rotation works under the hood | [Rotation & Matching Deep Dive](Rotation-And-Matching.md) |
| Design a new structure without hitting common pitfalls | [Pattern Design Guide](Pattern-Design-Guide.md) |
| Build a structure with a machine-like core that has its own facing | [Directional Cores Guide](Directional-Cores-Guide.md) |
| Use shapeless structures, free blocks, JSON definitions, ghost overlay, JEI/REI/EMI | [Advanced Features](Advanced-Features.md) |
| Debug why a structure won't match or form | [FAQ & Troubleshooting](FAQ-Troubleshooting.md) |
| Port code written against the old `PatternBuilder` API | [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md) |

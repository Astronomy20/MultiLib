# MultiLib Wiki

MultiLib is a NeoForge 1.21.1 (Java 21) **library mod** that lets other mods define and detect multiblock structures (a fixed 3D arrangement of blocks) without writing their own block-by-block matching logic. You describe a pattern with a fluent builder, give it an action to run when the structure is completed, and MultiLib takes care of detecting it in the world when a player places the right block.

> **Status note:** this wiki documents the pattern system as it exists today in the codebase (`PatternBuilder` / `PatternManager` / `PatternMatcher` / `PatternRegistry` / `PatternAction`). It does not describe planned or in-progress features that are not yet present in the source.

## Table of contents

1. [Getting Started](Getting-Started.md) — adding MultiLib as a dependency and registering your first pattern
2. [Core Concepts](Core-Concepts.md) — patterns, keys, layers, origin, registration, the activation flow
3. [Pattern Design Guide](Pattern-Design-Guide.md) — how to lay out layers/keys correctly, centering rules, common mistakes
4. [Rotation & Matching Deep Dive](Rotation-And-Matching.md) — how `PatternMatcher` searches for a match, what each rotation flag actually does, known limitations
5. API Reference
   - [PatternBuilder](api-reference/PatternBuilder.md)
   - [PatternManager](api-reference/PatternManager.md)
   - [PatternMatcher](api-reference/PatternMatcher.md)
   - [PatternRegistry](api-reference/PatternRegistry.md)
   - [PatternAction](api-reference/PatternAction.md)
   - [RotationUtils](api-reference/RotationUtils.md)
   - [MultiLibAPI](api-reference/MultiLibAPI.md)
6. [FAQ & Troubleshooting](FAQ-Troubleshooting.md)
7. Worked Examples *(on hold — see [Core Concepts § Known Limitations](Core-Concepts.md#known-limitations))*

The Worked Examples section is intentionally on hold: one of the two planned examples (a fixed-facing "core with a main face" structure) depends on a matcher limitation — `allowHorizontalRotation` is currently not enforced — that needs a product decision (fix the matcher vs. document the manual workaround) before it can be written accurately.

## Quick orientation

| If you want to... | Read |
|---|---|
| Add MultiLib to your mod and register a pattern in 5 minutes | [Getting Started](Getting-Started.md) |
| Understand what a "pattern", "key" and "origin" mean in MultiLib | [Core Concepts](Core-Concepts.md) |
| Look up a specific method/class signature | [API Reference](#5-api-reference) |
| Understand exactly how matching/rotation works under the hood | [Rotation & Matching Deep Dive](Rotation-And-Matching.md) |
| Design a new pattern without hitting common pitfalls | [Pattern Design Guide](Pattern-Design-Guide.md) |
| Debug why a pattern won't match | [FAQ & Troubleshooting](FAQ-Troubleshooting.md) |

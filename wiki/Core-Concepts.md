[← Back to Home](Home.md)

# Core Concepts

This page explains the model MultiLib uses to describe and detect a multiblock structure. Read this before designing your own structures — the coordinate system and symbol rules below are not optional details, they're the contract the matcher relies on.

## What is a "definition" in MultiLib?

A **definition** (`MultiblockDefinition`) is an immutable description of:

- a `ResourceLocation` **id** — how the structure is referenced (JEI/REI/EMI, JSON, wrench diagnostics, `MultiLibAPI.getDefinition(id)`),
- a set of **symbols** — single characters mapped to `BlockIngredient`s,
- one or more **layers** — horizontal slices of the structure described with those symbols (or, alternatively, a procedural `PatternProvider`, or a `shapeless()` flood-fill definition — see [Advanced Features](Advanced-Features.md)),
- a **core symbol** and/or **activation symbol** — which cell(s) matter for triggering/anchoring,
- rotation rules (`RotationMode` and/or granular `allowRotation(...)`),
- a **formation mode** — whether the structure forms automatically, only via wrench, or both,
- callbacks (`onFormed`, `onBroken`, `onTick`, `onAmbient`) and an optional `validator`.

You never construct a `MultiblockDefinition` directly; you assemble it with `MultiblockBuilder` (via `MultiLibAPI.define(id)`) and finish with `.build()`.

## Symbols and `BlockIngredient`

A symbol is a single `char` bound to a `BlockIngredient` via `.key(char, BlockIngredient)` (or the shorthand `.key(char, Block)`, which wraps it in a single-block ingredient). Symbols are **global to the whole definition**, not per-layer — the same character means the same ingredient on every layer you add.

`BlockIngredient` is richer than "one exact block" — see [BlockIngredient reference](api-reference/BlockIngredient.md) for the full list, but in short:

| Factory | Matches |
|---|---|
| `BlockIngredient.of(Block)` | Exactly one block |
| `BlockIngredient.ofState(Block).require(property, value)...build()` | One block with specific blockstate properties |
| `BlockIngredient.tag(TagKey<Block>)` | Any block in a tag |
| `BlockIngredient.anyOf(ingredient...)` | Any of several ingredients |
| `BlockIngredient.predicate(Predicate<BlockState>)` | Arbitrary logic |
| `BlockIngredient.any()` | Always matches (any block, including air) |

The space character (`' '`) is reserved and always means **"don't care / no constraint here"** — it is never treated as a symbol. Use it for empty cells inside a layer's bounding box. Any character that appears in a layer string but was never registered with `.key(...)` is silently ignored as if it were a space — there is no validation that catches typos in layer strings against your symbol map.

## Core and activation symbols

Two distinct roles, often — but not always — the same symbol:

- **Core** (`.core(char)`): the symbol representing the structure's "main" block, typically a block-entity-backed controller. Used as the anchor for the ghost overlay, auto-place, wrench diagnostics, and (if you use `AbstractMultiblockControllerBE`) where formed/unformed state actually lives.
- **Activation** (`.activation(char)`): the symbol whose *placement* should trigger an automatic formation check. Calling `.core(char)` **also sets activation to that same symbol if activation wasn't set explicitly** — so in the common case where "placing the controller completes the structure," you only need `.core(...)`.

You can split them: e.g. activation = the last body block placed, core = a separate controller block elsewhere in the pattern, if your structure's "last placed block" and "logical controller" aren't the same cell.

`MultiblockDefinition.matchesActivationOrCore(BlockState)` is what the wrench and periodic-validation logic use to decide "does this block belong to this definition's trigger set at all."

## Layers and the coordinate system

A layer is added with `.layer(String... rows)` — the single, only attribute for declaring layers. Each call to `.layer(...)` adds **one horizontal (Y) slice** of the structure:

- **Row order within a layer → Z axis.** The first string you pass is the row at the lowest Z offset, each subsequent string increases Z by 1.
- **Character order within a row → X axis.** The leftmost character is the lowest X offset, increasing left-to-right.
- **Order of `.layer(...)` calls → Y axis, top to bottom.** The **first** `.layer(...)` call is the **top** of the structure. The **last** `.layer(...)` call is the **bottom**.

> ⚠️ **This is the opposite of the old `PatternBuilder` API**, where the first `.layer(...)` call was the bottom. If you're porting old code, flip your `.layer(...)` call order — see [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md).

```java
.layer("PPP",   // top (first call) — relY = 0
       " P ",
       " G ")
.layer("POP",   // bottom (last call) — relY = -1
       " P ",
       " G ")
```

Each layer is centered independently: the **center column** is `row.length() / 2` (integer division) and the **center row** is `layer.size() / 2`. All rows within one `.layer(...)` call should have equal length — the matcher derives a layer's width from its first row.

The definition's **origin** (what `ctx.instance().getOrigin()` returns in callbacks) is the world position corresponding to the pattern's top-layer center cell, in whatever orientation actually matched.

## Formation modes

`FormationMode` governs how a structure is allowed to form:

| Mode | Automatic (block placement) | Wrench-triggerable |
|---|---|---|
| `FormationMode.AUTOMATIC` | ✅ | ❌ |
| `FormationMode.WRENCH` | ❌ | ✅ |
| `FormationMode.AUTOMATIC_AND_WRENCH` | ✅ | ✅ |

`FormationMode` is not a plain enum — it's an open, extensible registry (`FormationMode.register(id, allowsAutomatic, allowsWrench)`), so third-party mods can define their own trigger semantics (e.g. a redstone-pulse-triggered mode) as long as their own code decides when to call `BlockActivationHandler.triggerFormationAt(level, pos)`.

A "wrench" is any `Item` implementing the marker interface `IMultiblockWrench` — **MultiLib itself ships no wrench item**; you implement the interface on your own tool (see `ExampleWrenchItem` in the source tree for a reference implementation) or on any existing item you own.

## Registration and lookup

`.build()` does three things:

1. Validates the definition (at least one layer / a `PatternProvider` / `shapeless()`; core-symbol consistency between the builder and any block-level `.core(id)` declaration; geometry constraints like `unique()`/`surfaceOnly()`/etc. — see [Pattern Design Guide](Pattern-Design-Guide.md)).
2. Constructs the immutable `MultiblockDefinition`.
3. Registers it into `MultiblockRegistry`, indexed by the candidate blocks of its symbols — so a block placement only checks definitions that could plausibly involve the placed block, not every registered definition in the game.

If validation fails (e.g. a conflicting core declaration), registration is skipped and an error is logged — `.build()` still returns a `MultiblockDefinition` object, but it will never be matched against the world. Use `.buildWithoutRegistering()` if you want the object without touching the registry at all (e.g. unit tests).

## Activation flow

1. A block is placed in the world (`BlockEvent.EntityPlaceEvent`, server side only).
2. `BlockActivationHandler` looks up every registered definition that lists the placed block as a candidate (`MultiblockRegistry.getCandidatesFor`), keeping only those whose formation mode allows automatic triggering and whose activation symbol matches the placed block.
3. For each candidate, `PatternMatcher.matches(...)` (which dispatches to the shaped, shapeless, or functional matcher depending on the definition) searches for a match around the placed position, in every orientation the definition allows — see the [Rotation & Matching Deep Dive](Rotation-And-Matching.md).
4. On a match: if a `validator` is set, it runs first and can veto formation (`ValidationResult.Invalid`). Otherwise a `MultiblockFormedEvent` (cancellable, NeoForge event bus) fires; if not cancelled, a new `MultiblockInstance` is created and registered in the world's `WorldMultiblockTracker` (a `SavedData`, so it persists across restarts), every `onFormed` callback runs, the core's `AbstractMultiblockControllerBE.onStructureFormed(...)` fires if applicable, and every part block entity implementing `IMultiblockPart` gets `onJoinedStructure(...)`.
5. Breaking any block that's part of a **tracked, formed** instance fires the mirror path: `MultiblockBrokenEvent`, every `onBroken` callback, the core's `onStructureBroken(...)`, and every part's `onLeftStructure()` — then the instance is removed from the tracker. This is a real change from the old API: **formed structures are now tracked and do react to being broken**, not just to being placed.
6. If the definition has an `onTick` or `onAmbient` callback, `WorldMultiblockTracker.tick(...)` (driven by `LevelTickEvent.Post`) invokes it for every tracked instance every tick (`onTick`) or every N ticks (`onAmbient`, interval set via `.onAmbient(callback, intervalTicks)`).
7. If a controller block entity has `setValidationInterval(ticks)` configured, it periodically re-validates its own structure (still present?) and can also attempt formation periodically while unformed — this is what lets a `FormationMode.WRENCH`-eligible structure be discovered without an explicit wrench click, if you wire it that way.

## The controller block-entity pattern

For structures with meaningful state (running/idle/error, a menu, per-tick logic), extend `AbstractMultiblockControllerBE` for your core's block entity and `AbstractMultiblockControllerBlock` for the core's `Block`. This gives you:

- `MultiblockState` tracking (`UNFORMED` / `IDLE` / `RUNNING` / `ERROR` by default, extensible via your own `MultiblockState` implementations),
- automatic NBT persistence of state and the active instance id,
- `onFormed(ctx)` / `onBroken(ctx)` / `onStateChanged(prev, next)` / `serverTick()` hooks to override,
- automatic model-hiding wiring if the definition uses `.model(...)` (see [Advanced Features](Advanced-Features.md#master-dummy-model)),
- a `useWithoutItem` hook (`openMenu(...)`) only reachable once the structure `isFormed()`.

See [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md) for the full API, and the `ExampleControllerBE`/`ExampleControllerBlock` classes in the source tree for a minimal working reference.

## What changed from the old API (summary)

If you're familiar with the earlier `PatternBuilder`/`PatternManager`/`PatternAction` system, the load-bearing differences are:

- Layer order is **reversed** (first call = top, not bottom).
- Rotation is now **actually enforced** — `RotationMode.NONE` genuinely disables rotation matching (the old `allowHorizontalRotation` flag was a no-op bug).
- Structures are **tracked as persistent instances** with a real broken/formed lifecycle, not one-shot reactions to block placement.
- Ingredients are pluggable (`BlockIngredient`), not fixed to a single exact `Block`.
- A structure can be `shapeless()`, backed by a procedural `PatternProvider`, or defined declaratively via JSON/datapack — not just a fixed shaped layer grid.

See [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md) for a full checklist.

## See also

- [Getting Started](Getting-Started.md) — minimal working example
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Advanced Features](Advanced-Features.md)
- [MultiblockBuilder](api-reference/MultiblockBuilder.md), [MultiblockDefinition](api-reference/MultiblockDefinition.md), [BlockIngredient](api-reference/BlockIngredient.md), [Callbacks & Events](api-reference/Callbacks-And-Events.md)

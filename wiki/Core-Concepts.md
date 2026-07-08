[← Back to Home](index.md)

# Core Concepts

The model MultiLib uses to describe and detect a structure. The coordinate system and symbol rules below are the contract the matcher relies on — read them before designing structures.

## What is a definition?

A **definition** (`MultiblockDefinition`) is an immutable description of:

- an **id** (`ResourceLocation`) — how the structure is referenced (recipe viewers, JSON, wrench, `MultiLibAPI.getDefinition`);
- **symbols** — single characters mapped to `BlockIngredient`s;
- **layers** — horizontal slices described with those symbols (or a procedural `PatternProvider`, or a `shapeless()` flood-fill — see [Advanced Features](Advanced-Features.md));
- a **core** and/or **activation** symbol;
- rotation rules (`RotationMode` and/or `allowRotation(...)`);
- a **formation mode**;
- callbacks (`onFormed`, `onBroken`, `onTick`, `onAmbient`) and an optional `validator`.

You never construct one directly — assemble it with `MultiblockBuilder` (via `MultiLibAPI.define(id)`) and finish with `.build()`.

## Symbols and `BlockIngredient`

A symbol is a `char` bound to a `BlockIngredient` via `.key(char, BlockIngredient)` (or `.key(char, Block)`). Symbols are **global to the definition** — the same character means the same ingredient on every layer.

`BlockIngredient` is more than "one exact block" ([full reference](api-reference/BlockIngredient.md)):

| Factory | Matches |
|---|---|
| `BlockIngredient.of(Block)` | Exactly one block |
| `BlockIngredient.ofState(Block).require(property, value)…build()` | A block with specific properties |
| `BlockIngredient.tag(TagKey<Block>)` | Any block in a tag |
| `BlockIngredient.anyOf(ingredient…)` | Any of several ingredients |
| `BlockIngredient.predicate(Predicate<BlockState>)` | Arbitrary logic |
| `BlockIngredient.any()` | Anything, including air |

Space (`' '`) is reserved: it means "no constraint" and is never a symbol. A character that appears in a layer but was never registered with `.key(...)` is silently treated as a space — layer strings are not validated against the symbol map, so typos pass unnoticed.

## Core and activation symbols

Two roles, often the same symbol:

- **Core** (`.core(char)`) — the structure's main block, usually a controller. Anchors the ghost overlay, auto-place, wrench diagnostics, and (with `AbstractMultiblockControllerBE`) where state lives.
- **Activation** (`.activation(char)`) — the symbol whose *placement* triggers an automatic check. `.core(char)` also sets activation to the same symbol unless activation was set explicitly, so the common "placing the controller completes the structure" case needs only `.core(...)`.

Split them when the last-placed block and the logical controller aren't the same cell. `matchesActivationOrCore(BlockState)` is what the wrench and periodic validation use to decide whether a block belongs to a definition's trigger set.

## Layers and the coordinate system

Each `.layer(String... rows)` call adds **one horizontal (Y) slice**:

- **Row order → Z.** First string = lowest Z, increasing per row.
- **Character order → X.** Leftmost char = lowest X.
- **`.layer(...)` call order → Y, top to bottom.** First call = **top**, last call = **bottom**.

> ⚠️ **Opposite of the old `PatternBuilder` API**, where the first call was the bottom. Porting old code means flipping call order — see [Migrating](Migrating-From-PatternBuilder.md).

```java
.layer("PPP",   // top    → relY = 0
       " P ",
       " G ")
.layer("POP",   // bottom → relY = -1
       " P ",
       " G ")
```

Each layer is centered independently: center column = `row.length() / 2`, center row = `layer.size() / 2` (integer division). All rows in one call should be equal length — width is taken from the first row.

The **origin** (`ctx.instance().getOrigin()`) is the world position of the top-layer center cell, in whatever orientation matched.

## Formation modes

| Mode | Automatic (placement) | Wrench |
|---|---|---|
| `AUTOMATIC` | ✅ | ❌ |
| `WRENCH` | ❌ | ✅ |
| `AUTOMATIC_AND_WRENCH` | ✅ | ✅ |

`FormationMode` is an extensible registry, not a plain enum (`FormationMode.register(id, allowsAutomatic, allowsWrench)`), so a mod can define its own trigger semantics as long as its code decides when to call `BlockActivationHandler.triggerFormationAt(level, pos)`.

A "wrench" is any `Item` implementing `IMultiblockWrench`. **MultiLib ships no wrench item** — implement the interface on your own tool (`ExampleWrenchItem` is a reference).

## Registration and lookup

`.build()`:

1. Validates the definition (geometry present; core-symbol consistency; constraints like `unique()`/`surfaceOnly()` — see [Pattern Design Guide](Pattern-Design-Guide.md)).
2. Constructs the immutable `MultiblockDefinition`.
3. Registers it in `MultiblockRegistry`, indexed by its symbols' candidate blocks — so a placement only checks definitions that could involve the placed block.

If validation fails, registration is skipped and an error is logged; `.build()` still returns the object, but it's never matched. Use `.buildWithoutRegistering()` to skip the registry entirely (e.g. tests).

When the same block is a valid core/activation symbol for **more than one** definition, higher `priority(...)` wins by default; a per-position override can pin a specific definition to a specific spot — see [Ambiguity & Preferences](api-reference/Ambiguity-And-Preferences.md).

## Activation flow

1. A block is placed (`BlockEvent.EntityPlaceEvent`, server side).
2. `BlockActivationHandler` finds definitions listing that block as a candidate, keeping those whose formation mode allows automatic triggering and whose activation symbol matches.
3. `PatternMatcher.matches(...)` (shaped/shapeless/functional) searches around the position in every allowed orientation — see [Rotation & Matching](Rotation-And-Matching.md).
4. On a match: a `validator`, if set, runs first and can veto (`ValidationResult.Invalid`). Otherwise a cancellable `MultiblockFormedEvent` fires; if not cancelled, a `MultiblockInstance` is created and stored in the world's `WorldMultiblockTracker` (a `SavedData`, persistent across restarts), `onFormed` callbacks run, the controller's `onStructureFormed(...)` fires, and each part implementing `IMultiblockPart` gets `onJoinedStructure(...)`.
5. Breaking any block of a tracked instance mirrors this: `MultiblockBrokenEvent`, `onBroken` callbacks, `onStructureBroken(...)`, each part's `onLeftStructure()`, then removal from the tracker.
6. `onTick`/`onAmbient` callbacks run via `WorldMultiblockTracker.tick(...)` (driven by `LevelTickEvent.Post`) — every tick, or every N ticks respectively.
7. A controller with `setValidationInterval(ticks)` periodically re-validates its structure and can attempt formation while unformed.

## The controller block-entity pattern

For structures with state (running/idle/error, a menu, per-tick logic), extend `AbstractMultiblockControllerBE` for the core's block entity and `AbstractMultiblockControllerBlock` for its block. You get:

- `MultiblockState` tracking (`UNFORMED`/`IDLE`/`RUNNING`/`ERROR`, extensible);
- automatic NBT persistence of state and instance id;
- `onFormed` / `onBroken` / `onStateChanged` / `serverTick` hooks;
- automatic model-hiding if the definition uses `.model(...)` ([Master-Dummy model](Advanced-Features.md#master-dummy-model));
- an `openMenu(...)` hook reachable only once `isFormed()`.

See [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md) and the `ExampleControllerBE`/`ExampleControllerBlock` reference classes.

## What changed from the old API (summary)

Coming from the earlier `PatternBuilder`/`PatternManager` system, the load-bearing differences:

- Layer order is **reversed** (first call = top).
- Rotation is **enforced** — `RotationMode.NONE` genuinely disables it (the old flag was a no-op bug).
- Structures are **tracked as persistent instances** with a real form/break lifecycle, not one-shot placement reactions.
- Ingredients are pluggable (`BlockIngredient`), not a single exact `Block`.
- A structure can be `shapeless()`, procedural, or JSON-defined — not just a shaped grid.

Full checklist: [Migrating](Migrating-From-PatternBuilder.md).

## See also

- [Getting Started](Getting-Started.md), [Rotation & Matching](Rotation-And-Matching.md), [Pattern Design Guide](Pattern-Design-Guide.md), [Advanced Features](Advanced-Features.md)
- [MultiblockBuilder](api-reference/MultiblockBuilder.md), [MultiblockDefinition](api-reference/MultiblockDefinition.md), [BlockIngredient](api-reference/BlockIngredient.md), [Callbacks & Events](api-reference/Callbacks-And-Events.md)

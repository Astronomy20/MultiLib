[← Back to Home](Home.md)

# FAQ & Troubleshooting

## My structure never forms

Work through this list in order:

1. **Does `.build()` actually get called, and does it run during mod setup?** Registration happens the moment `.build()` runs — make sure your `registerAll()` (or equivalent) is actually invoked, e.g. from `FMLCommonSetupEvent`.
2. **Did registration silently fail?** `.build()` has two different failure modes: if the id is unset, or there are no layers/`PatternProvider`/`shapeless()`, it **throws `IllegalStateException`** immediately (you'll see it, not a silent failure). But if a block-level `.core(id)` declaration conflicts with the definition's own `.core(char)`, or a geometry constraint (`.unique`/`.surfaceOnly`/`.frameOnly`/`.insideOnly`) is violated, it logs an error and *silently* skips registration without throwing — `.build()` still returns an object, it's just never registered. Check your logs — see [Core Concepts § Registration and lookup](Core-Concepts.md#registration-and-lookup).
3. **Are you building the pattern in the exact relative layout you described?** Row order in a `.layer(...)` call → Z axis, character order → X axis, **`.layer(...)` call order → Y axis, top to bottom** (⚠️ opposite of the old `PatternBuilder` API — see [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md)).
4. **Did a typo turn a symbol into a no-op?** Any character in a layer row that wasn't registered via `.key(...)` is silently treated as `' '` (no constraint) — this doesn't cause a build error. Double-check every character used in your `.layer(...)` strings has a matching `.key(...)` call.
5. **Is the formation mode what you expect?** `FormationMode.WRENCH` (or a custom mode with `allowsAutomatic = false`) never triggers from plain block placement — you (or the player) need to use an `IMultiblockWrench` item on it. See [Core Concepts § Formation modes](Core-Concepts.md#formation-modes).
6. **Is your activation/core symbol's `BlockIngredient` enumerable?** A tag/predicate/`any()`-based activation symbol still works, but if it's not correctly written, the definition ends up "always-checked" and mismatches are easy to miss — verify with a simple `BlockIngredient.of(...)` first, then generalize once the shape is confirmed correct.
7. **Does a `validator` silently veto formation?** If `.validator(...)` is set, it can return `ValidationResult.Invalid(...)` after a successful shape match, preventing the instance from ever being created — check the validator's logic and the `MultiblockFormedEvent` isn't being cancelled by another mod either.
8. **Is another registered definition matching first?** Definitions are tried in `priority` order (descending), **JSON-defined before Java-defined on ties** (the same "data overrides hardcoded defaults" convention vanilla uses for recipes/loot tables/tags) — if two definitions could plausibly match the same placed block, the higher-priority one wins and the other's callbacks never run for that placement. See [`MultiblockRegistry.getCandidatesFor`](api-reference/MultiblockInstance-And-Registry.md#multiblockregistry).
9. **Are you placing the block server-side?** Matching only runs from `BlockEvent.EntityPlaceEvent` on a `ServerLevel` (via `BlockActivationHandler`) — this fires normally for both singleplayer (integrated server) and dedicated server, but custom placement logic that bypasses the normal event won't trigger it.

## My structure matches in the wrong orientation, or doesn't match when rotated

This system fixed the old bug where rotation flags did nothing — rotation really is enforced now. Check:

- `RotationMode.NONE` means **only the exact built orientation matches** — if you want any horizontal facing to work, use `RotationMode.HORIZONTAL` (the default) or an explicit `.allowRotation(RotationAxis.Y, ...)`.
- If you called `.allowRotation(...)` at all, it **replaces** `RotationMode` entirely rather than adding to it — the matcher only tries the identity orientation plus exactly what you declared.
- `RotationMode.ALL` (or an `.allowRotation(...)` entry on `X`/`Z`) allows the structure tipped onto its side — if that's not intended, you likely want `HORIZONTAL` instead.

See the [Rotation & Matching Deep Dive](Rotation-And-Matching.md) for the full mechanics.

## My core has its own facing but the ghost overlay/auto-place preview doesn't follow it

You need `.mainFace()` declared on the core's `BlockDefinition` (`MultiLibAPI.block(coreBlock).mainFace().build()`), not just a `HORIZONTAL_FACING`-style blockstate property on the block itself. Without it, the preview follows the *player's* look direction rather than the core's actual placed facing. See the [Directional Cores Guide](Directional-Cores-Guide.md).

## Can I match a tag, or something more flexible than one exact block?

Yes — use `BlockIngredient.tag(tagKey)`, `.predicate(...)`, or `.anyOf(...)` instead of `BlockIngredient.of(block)`. See the [`BlockIngredient` reference](api-reference/BlockIngredient.md). Just be aware of the "always-checked" performance caveat if you use one of these for your activation/core symbol.

## Can I react to the structure being broken, not just formed?

Yes — this is a real, tracked lifecycle now, not a one-shot reaction to placement. Use `.onBroken(cb)` on the builder, or subscribe to `MultiblockBrokenEvent` on the NeoForge event bus if you need cross-mod visibility. See [Callbacks & Events](api-reference/Callbacks-And-Events.md).

## Can I define structures in JSON/datapacks instead of Java?

Yes — see [Advanced Features § JSON/datapack definitions](Advanced-Features.md#jsondatapack-definitions). JSON-defined structures are swapped out cleanly on `/reload` without touching Java-defined ones (`MultiblockRegistry.registerJson`/`clearJsonDefinitions`).

## My structure forms but blocks aren't removed / consumed afterward

Formation never removes or consumes blocks automatically — the built structure stays exactly as placed. If you want to visually "collapse" the structure into a single-block appearance once formed (without physically changing the blocks), that's what `.model(...)` (Master-Dummy) is for — see [Advanced Features § Master-Dummy model](Advanced-Features.md#master-dummy-model). If you actually need to consume/destroy blocks, do that yourself from your `onFormed` callback.

## My part block disappeared after the structure formed (but its hitbox is still there)

This is expected if the definition uses `.model(...)`: the moment the structure forms, every part block's model becomes invisible (hitbox and identity unchanged) except the core and any symbols listed in `.keepVisible(char...)`. This is wired automatically by `AbstractMultiblockControllerBE.onStructureFormed`/`onStructureBroken` and is not something you toggle manually. If you have interactive blocks (e.g. IO ports) that need to stay visible, add their symbols to `.keepVisible(...)` when building the definition. See [Advanced Features § Master-Dummy model](Advanced-Features.md#master-dummy-model) and [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md).

## Where do I find working, minimal examples?

The `net.astronomy.multilib.example` package has several:

- `ExamplePattern` — the "controller as both core and activation symbol" pattern from [Getting Started](Getting-Started.md), including a Master-Dummy model and `onFormed`/`onBroken` callbacks.
- `ExampleDirectionalPattern` + `ExampleSetup.DIRECTIONAL_CONTROLLER_BLOCK` — a `.mainFace()`-based fixed-facing core, see the [Directional Cores Guide](Directional-Cores-Guide.md).
- `ExampleControllerBE`/`ExampleControllerBlock` — a minimal `AbstractMultiblockControllerBE`/`Block` pair, see [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md).
- `ExampleWrenchItem` — a reference `IMultiblockWrench` implementation (MultiLib itself ships no wrench item — you must provide your own).

## See also

- [Core Concepts](Core-Concepts.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)
- [Advanced Features](Advanced-Features.md)
- [Migrating from the old PatternBuilder API](Migrating-From-PatternBuilder.md)

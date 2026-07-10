[← Back to Home](index.md)

# FAQ & Troubleshooting

## My structure never forms

Work through this in order:

1. **Is `.build()` actually called during mod setup?** Registration happens when `.build()` runs — make sure your registration method is invoked (e.g. from `FMLCommonSetupEvent`).
2. **Did registration silently fail?** An unset id or missing geometry **throws** `IllegalStateException` (visible). But a conflicting block-level `.core(id)` or a violated geometry constraint (`.unique`/`.surfaceOnly`/…) only *logs* an error and skips registration — `.build()` still returns an unregistered object. Check your logs ([details](Core-Concepts.md#registration-and-lookup)).
3. **Is the layout right?** Rows → Z, characters → X, **`.layer(...)` order → Y top-to-bottom**.
4. **A typo silently disabled a symbol?** A character with no `.key(...)` is treated as `' '` (no constraint), with no build error. Verify every layer character has a key.
5. **Right formation mode?** `WRENCH` (or any mode with `allowsAutomatic = false`) never triggers from placement — use a wrench ([formation modes](Core-Concepts.md#formation-modes)).
6. **A validator vetoing?** `.validator(...)` can return `ValidationResult.Invalid` after a shape match. Also check no other mod cancels `MultiblockFormedEvent`.
7. **Another definition matching first?** Definitions are tried by `priority` (descending), JSON before Java on ties. A higher-priority match wins ([`getCandidatesFor`](api-reference/MultiblockInstance-And-Registry.md#multiblockregistry)).
8. **Server-side placement?** Matching runs only from `BlockEvent.EntityPlaceEvent` on a `ServerLevel`. Custom placement bypassing the event won't trigger it.

## It matches in the wrong orientation (or won't when rotated)

- `RotationMode.NONE` matches **only the built orientation**. For any horizontal facing use `HORIZONTAL` (default).
- Any `.allowRotation(...)` **replaces** `RotationMode` — the matcher tries only the identity plus what you declared.
- `RotationMode.ALL` (or an `X`/`Z` `.allowRotation`) allows tipping onto the side; if unintended, use `HORIZONTAL`.

Full mechanics: [Rotation & Matching](Rotation-And-Matching.md).

## The preview doesn't follow my directional core

Declare `.mainFace()` on the core's `BlockDefinition` — a facing property alone isn't enough. See the [Directional Cores Guide](Directional-Cores-Guide.md).

## Can I match a tag or something more flexible?

Yes — `BlockIngredient.tag(...)`, `.predicate(...)`, `.anyOf(...)` ([reference](api-reference/BlockIngredient.md)). Mind the always-checked caveat if used on the activation/core symbol.

## Can I react to the structure breaking?

Yes — `.onBroken(cb)`, or subscribe to `MultiblockBrokenEvent` for cross-mod visibility ([Callbacks & Events](api-reference/Callbacks-And-Events.md)).

## Can I define structures in JSON?

Yes — [Advanced Features § JSON/datapack definitions](Advanced-Features.md#jsondatapack-definitions). JSON definitions reload cleanly without touching Java ones.

## Blocks aren't consumed after forming

Formation never removes blocks — the structure stays as placed. For a single-block *appearance*, use `.model(...)` ([Master-Dummy](Advanced-Features.md#master-dummy-model)). To actually consume blocks, do it in your `onFormed` callback.

## A part block vanished after forming (hitbox remains)

Expected with `.model(...)`: part models hide on formation (hitbox/identity unchanged) except the core and `.keepVisible(char...)` symbols. Add interactive blocks (IO ports) to `.keepVisible(...)`. See [Master-Dummy model](Advanced-Features.md#master-dummy-model).

## Where are working examples?

The `net.astronomy.multilib.example` package:

- `ExamplePattern` — controller as core+activation, with a Master-Dummy model and callbacks.
- `ExampleDirectionalPattern` — a `.mainFace()` fixed-facing core.
- `ExampleControllerBE`/`ExampleControllerBlock` — a minimal controller pair.
- `ExampleWrenchItem` — a reference `IMultiblockWrench` (MultiLib ships no wrench itself).

## See also

- [Core Concepts](Core-Concepts.md), [Pattern Design Guide](Pattern-Design-Guide.md), [Rotation & Matching](Rotation-And-Matching.md), [Advanced Features](Advanced-Features.md)

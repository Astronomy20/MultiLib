[← Back to Home](Home.md)

# FAQ & Troubleshooting

## My pattern never matches

Work through this list in order:

1. **Did you call `.action(...)` before `.build()`?** A pattern built without an action is never registered into `PatternRegistry` and can never match — see [PatternBuilder.build()](api-reference/PatternBuilder.md#build).
2. **Is `registerAll()` (or equivalent) actually being called?** Patterns must be registered during mod setup (`FMLCommonSetupEvent`), and the registration call must actually execute — a missing call site is the most common cause. Check your logger output for confirmation, the way `ExamplePattern`/`MultiLib.commonSetup` does.
3. **Are you placing the blocks in the exact relative layout you described?** Remember: row order in a `.layer(...)` call → Z, character order → X, `.layer(...)` call order → Y bottom-to-top. See [Core Concepts](Core-Concepts.md#layers-and-the-coordinate-system).
4. **Did a typo turn a key character into a no-op?** Any character in a layer row that wasn't registered via `.key(...)` is silently treated as `' '` (no constraint) — it won't cause a build error. Double check every character used in your `layer(...)` strings has a matching `.key(...)` call.
5. **Are all rows in each layer the same length?** Mismatched row lengths in the same `.layer(...)` call produce undefined matching behavior, not an exception. See [Pattern Design Guide § 3](Pattern-Design-Guide.md#3-keep-every-row-in-a-layer-the-same-length).
6. **Is another registered pattern "stealing" the placement?** Only the **first** matching pattern for a given placed block wins; if a different, unintended pattern using the same key block matches first, your intended pattern's action never runs. See [Pattern Design Guide § 7](Pattern-Design-Guide.md#7-avoid-key-collisions-across-unrelated-patterns).
7. **Are you placing the block server-side?** Matching only runs in `BlockEvent.EntityPlaceEvent` on a `ServerLevel` — this fires normally for both singleplayer (integrated server) and dedicated server placement, but if you're testing via some non-standard placement path (commands, custom block-placing logic that bypasses the normal event), confirm that path still fires `BlockEvent.EntityPlaceEvent`.

## My pattern matches in the wrong orientation / matches when I didn't want it to

`allowHorizontalRotation(false)` does **not** currently restrict horizontal matching — it has no effect on the matcher. All 4 horizontal rotations are always tried. If you need a fixed facing, you must reject unwanted orientations yourself inside your `PatternAction`. See [Rotation & Matching Deep Dive](Rotation-And-Matching.md#what-each-rotation-flag-actually-does-today).

## My pattern matches even though one block is "obviously" wrong

Check whether the "wrong" block still satisfies `state.is(expectedBlock)` — matching compares `Block` identity only, not full blockstate (no property matching, no tag matching). Two visually different blockstates of the same `Block` (e.g. different rotation/waterlogged variants of the same block type, where applicable) are indistinguishable to the matcher.

## Can I match a tag instead of a specific block?

Not with the current `key(char, Block)` API — keys bind to a single concrete `Block`. There is no tag-aware or predicate-based key variant in the current source.

## Can I have the structure "break" trigger something, the way placement does?

Not currently. There is no formed-state tracking and no break/unform callback — matching only happens reactively on block placement. See [Core Concepts § Known Limitations](Core-Concepts.md#known-limitations).

## Can I unregister a pattern, or reload patterns from a datapack?

Not currently. `PatternRegistry` has no unregister method, and there's no JSON/datapack-driven pattern loading in this codebase — all patterns are registered in Java code at mod setup time.

## My action runs but blocks aren't removed afterward

Matching never removes blocks automatically. If your structure should be "consumed," call [`PatternAction.clearStructure(level, origin, pattern, transform)`](api-reference/PatternAction.md#clearstructureserverlevel-level-blockpos-origin-patternmanager-pattern-transformdata-transform) yourself from inside your action, passing the same `pattern` instance and the `transform` your action received.

## Where do I find a working, minimal example?

`ExamplePattern` (`src/main/java/net/astronomy/multilib/pattern/type/ExamplePattern.java`, registered from `MultiLib.commonSetup`) is the one fully working example in the current source tree — also reproduced in [Getting Started](Getting-Started.md). `MultiBlockPattern` and `SummonPattern` in the same package are currently empty placeholders, not usable references.

## See also

- [Core Concepts](Core-Concepts.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Rotation & Matching Deep Dive](Rotation-And-Matching.md)

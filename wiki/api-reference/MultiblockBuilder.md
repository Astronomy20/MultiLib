[← Back to Home](../index.md)

# `MultiblockBuilder`

Package: `net.astronomy.multilib.api.definition`

Fluent builder for one [`MultiblockDefinition`](MultiblockDefinition.md). Obtain an instance via `MultiLibAPI.define(id)` or `new MultiblockBuilder().id(id)`. Every setter returns `this`, so calls chain.

See [Core Concepts](../Core-Concepts.md) for the conceptual model. This page documents the full method surface.

## Identity & shape

### `id(ResourceLocation id)`
Sets the structure's id. Required - `.build()` throws if unset.

### `layer(String... rows)`
The single attribute for declaring layers. Appends one horizontal (Y) slice. **First call = top of the structure, last call = bottom** (opposite of the old `PatternBuilder` API - see [Migrating from the old PatternBuilder API](../Migrating-From-PatternBuilder.md)).

### `key(char symbol, Block block)`
Shorthand for `key(symbol, BlockIngredient.of(block))`.

### `key(char symbol, String blockOrTagId)`
String form: a block id (`"minecraft:iron_block"`) or, with a `#` prefix, a block tag (`"#c:storage_blocks/iron"`), parsed via [`BlockIngredient.parse(...)`](BlockIngredient.md#blockingredientparsestring-spec). The same two forms JSON and KubeJS take, without needing a Java `Block`/`TagKey` reference — mainly for scripted call sites. Throws `IllegalArgumentException` on a malformed or (for a plain block id) unregistered id.

### `key(char symbol, BlockIngredient ingredient)`
Binds a symbol to an ingredient. Symbols are global across all layers.

### `key(char symbol, BlockIngredient ingredient, WallSharingMode mode)`
Same as above, plus an explicit per-symbol wall-sharing override (highest priority in the override chain - see [Advanced Features § Wall sharing](../Advanced-Features.md#wall-sharing)).

## Rotation

### `rotations(RotationMode mode)`
Coarse rotation control: `NONE`, `HORIZONTAL` (default), or `ALL`. See [Rotation & Matching Deep Dive](../Rotation-And-Matching.md).

### `allowRotation(RotationAxis axis, int... angles)`
Granular rotation control: allows the specific angle(s) (90/180/270/-90) around `axis`, in addition to the always-tried unrotated orientation. Omitting `angles` allows all three (90/180/270). **Declaring any rotation here takes over from `RotationMode`** - a Y-axis entry behaves like horizontal matching, an X or Z entry additionally enables tipped-over matching.

### `allowRotation(RotationAxis[] axes, int... angles)`
Same, for multiple axes at once - e.g. `.allowRotation(new RotationAxis[]{RotationAxis.X, RotationAxis.Z}, 180)`, or `.allowRotation(RotationAxis.values())` for every axis/angle.

### `allowRotation(AllowedRotation... rotations)`
For when different axes need different angles in one call - e.g. `.allowRotation(new AllowedRotation(RotationAxis.X, 90), new AllowedRotation(RotationAxis.Z, 180))`.

## Symbols: core, activation, priority

### `activation(char symbol)`
Sets the symbol whose placement triggers an automatic formation check.

### `core(char symbol)`
Sets the core symbol. **Also sets activation to the same symbol if activation wasn't already set.**

### `priority(int priority)`
Higher priority definitions are tried first when multiple definitions could match the same placed block (see [Core Concepts](../Core-Concepts.md#registration-and-lookup)). Default `0`; ties are broken in favor of JSON-defined definitions over code-defined ones (the same "data overrides hardcoded defaults" convention vanilla uses for recipes/loot tables/tags).

### `requireAirInEmptyPositions()`
Requires every "empty" (space) cell in the pattern's bounding box to actually be air in the world - otherwise those cells are unconstrained by default (this only affects `PatternProvider`-backed/functional definitions in practice; plain shaped layers already only check non-space symbols).

## Formation & lifecycle

### `formationMode(FormationMode mode)`
Sets `AUTOMATIC`, `WRENCH`, or `AUTOMATIC_AND_WRENCH` (or a custom registered mode). Default `AUTOMATIC`.

### `onFormed(MultiblockFormedCallback cb)`
Adds a callback invoked when the structure forms. Can be called multiple times - all are invoked, in registration order.

### `onBroken(MultiblockBrokenCallback cb)`
Adds a callback invoked when a block of a **tracked, formed** instance is broken. Can be called multiple times.

### `onTick(MultiblockTickCallback cb)`
Sets the per-tick callback, invoked once per server tick for every tracked, formed instance of this definition. Only one - later calls replace earlier ones.

### `onAmbient(MultiblockAmbientCallback cb, int intervalTicks)`
Sets a periodic callback invoked at most every `intervalTicks` ticks per instance (checked, not guaranteed exact - see [`WorldMultiblockTracker`](MultiblockInstance-And-Registry.md)).

### `validator(MultiblockValidator validator)`
Sets a validator run **before** formation completes; can veto formation by returning `ValidationResult.Invalid(...)`. See [Callbacks & Events](Callbacks-And-Events.md#multiblockvalidator).

## Shape sources

### `pattern(PatternProvider provider)`
Uses a procedural `PatternProvider` instead of textual layers - see [Advanced Features § Procedural patterns](../Advanced-Features.md#procedural-patterns-patternprovider).

### `boundingBox(int x, int y, int z)`
Overrides the bounding box used with a `PatternProvider` (defaults to the provider's own `getSize()`).

### `shapeless()`
Marks the definition as shapeless - matched via flood-fill instead of a fixed grid. See [Advanced Features § Shapeless structures](../Advanced-Features.md#shapeless-structures).

### `minSize(int x, int y, int z)` / `maxSize(int x, int y, int z)`
Bounding-box constraints for shapeless structures. `maxSize` also bounds the flood-fill search radius. Defaults: min `(0,0,0)`, max `(64,64,64)`.

## Variants

### `variant(String name, Consumer<VariantBuilder> config)`
Declares one named geometry variant — an alternate set of layers, with optional variant-local keys (`VariantBuilder.layer(...)` / `.key(...)`) — that forms under the *same id*, sharing every behavioral field (callbacks, formation mode, rotation, core/activation, priority, `formedProperty`, tiers, validator).

**Entirely opt-in.** A normal single-shape definition never calls this — the plain `.layer(...)` path is unchanged. Use `variant(...)` only for "one id, several shapes" (different sizes/orientations of one machine, GregTech-style). A lone variant is legal but pointless.

Once any variant is declared, all geometry must live inside `variant(...)` blocks — mixing a top-level `.layer(...)`, or an empty/duplicate name, throws at build time.

At `build()`, the **first** variant becomes this definition's own geometry (its name → `getVariantName()`); each later one becomes a derived `MultiblockDefinition` (`getVariantDefinitions()`) sharing the parent's id, never separately registered. Shared top-level keys apply to all variants; a variant's own key overrides for that variant only.

> **Declaration order matters.** Matching tries variants in order and stops at the first success. When one geometry is a superset of another, **declare the larger first** — otherwise a large build matches as the small variant and the wrench upgrade never triggers. The first variant is also the primary geometry for the ghost overlay/auto-place. See `multilib:example_variants` in the source for a demo.

For a definition that never calls `variant(...)`, `getVariantName()` is `"default"` — the same object as always, not a separate lookup. Variant names show as a recipe-viewer title suffix only for `variant(...)`-built definitions; the implicit `"default"` never does.

Formed instances report their variant via `MultiblockInstance.getVariant()` (NBT-persisted; old saves load as `"default"`). Wrenching a formed structure that's been rebuilt into a different declared variant upgrades it **in place** — same UUID, contents preserved, no form/break callbacks — see [`WrenchResult.VariantChanged`](Callbacks-And-Events.md#wrenchinteractionevent).

JSON uses a `"variants"` array (exclusive with top-level `"layers"`); KubeJS calls `.variant(...)` on the builder — see [KubeJS Integration](../KubeJS-Integration.md).

## Shapeless-only shape refinement

### `shell(BlockIngredient ingredient)`
Required ingredient for every boundary block of a shapeless structure, unless overridden per-face.

### `shellFace(Direction direction, BlockIngredient ingredient)`
Per-face override of the shell ingredient (e.g. a different block for the top face).

### `interior(BlockIngredient ingredient)`
Required ingredient for every strictly-interior block (not touching any boundary) of a shapeless structure.

### `require(BlockIngredient ingredient, int min, int max)`
Adds a shapeless-only count requirement: at least `min` and at most `max` matched blocks (anywhere in the flood-filled region) must satisfy `ingredient`.

## Optional & free-form positions

### `optional(char... symbols)`
Marks symbols whose cells are allowed to mismatch without failing the whole match (shaped definitions only).

### `optionalLayer(String... rows)`
Adds a layer that the matcher may try with or without, in addition to being appended like `.layer(...)`. Internally, the matcher tries every combination of included/excluded optional layers.

### `freeBlock(char symbol, BlockIngredient ingredient, int min, int max)`
Declares a symbol that isn't tied to fixed layer positions: instead, MultiLib scans the pattern's bounding box for *any* unclaimed cell matching `ingredient` and counts it toward this symbol, requiring between `min` and `max` matches.

### `freeBlock(char symbol, BlockIngredient ingredient, int min, int max, List<BlockPos> allowedPositions)`
Same, but restricted to a specific set of relative positions.

## Geometry constraints

### `unique(char... symbols)`
Each symbol must occur **exactly once** in the structure (statically validated at `.build()` time against the textual layers; for `freeBlock` symbols, this instead forces `min=max=1`).

### `surfaceOnly(char... symbols)` / `frameOnly(char... symbols)` / `insideOnly(char... symbols)`
Statically validated placement constraints checked against the textual layers at `.build()` time:
- `surfaceOnly`: must be on at least one boundary axis (an outer face).
- `frameOnly`: must be on at least two boundary axes (an edge/corner).
- `insideOnly`: must touch no boundary at all.

Violations are logged as errors at build time (definition registration still proceeds unless the *core* mismatch check separately fails - see `MultiblockDefinition`'s validation).

## Wall sharing

### `wallSharing(boolean enabled)`
Default wall-sharing behavior for this definition's non-core/non-activation symbols (used only if no more specific override applies). Defaults to `false` (wall sharing disabled) if never called - you must opt in explicitly with `wallSharing(true)`.

### `noWallSharing(char... symbols)`
Shorthand for setting `WallSharingMode.DISABLED` per symbol.

## Tiers

### `tier(char symbol, String name, Block... blocks)`
Declares one named tier level for `symbol`, backed by an explicit set of blocks. Call repeatedly for the same symbol from lowest to highest tier - each call's position in that order becomes its ordinal (0 = first/lowest), which is what a caller compares tiers by, not the name itself.

### `tier(char symbol, String name, TagKey<Block> tag)`
Same, but backed by a `TagKey` instead of a fixed block list - so a third-party addon can contribute its own blocks to this tier via datapack (adding to the tag) without this definition needing to know about them ahead of time.

### `tier(char symbol, String name, Map<String, Double> stats, Block... blocks)` / `tier(char symbol, String name, Map<String, Double> stats, TagKey<Block> tag)`
Same two declarations, with a **stat map** attached to the tier: arbitrary `key → value` numbers (e.g. `"speed" → 2.0`) your machine logic reads back from the resolved tier instead of `switch`-ing on tier names. See [MultiblockTier § stats](MultiblockTier.md#multiblocktierresolution) for the resolution-side accessors.

### `tierStats(char symbol, String tierName, Map<String, Double> stats)`
Attaches (or replaces) the stat map on an **already-declared** tier, identified by symbol + tier name. Fail-fast: throws `IllegalArgumentException` if no tier was declared under that symbol/name - a typo here should break at mod load, not silently resolve to no stats.

Tiers and their stats are Java/KubeJS-builder-only - the JSON datapack format has no tier support.

See [MultiblockTier](MultiblockTier.md) for resolving which tier is actually present in a formed instance.

## Formed-state property

### `formedProperty(String propertyName)`
When set, forming the structure flips the `BooleanProperty` with this name to `true` on every member block **that declares it** (blocks without the property are silently skipped), and breaking flips it back to `false`. Happens after the instance is registered and *before* `onFormed` callbacks run, so callbacks observe the final world state. Taken as a `String` (not a `BooleanProperty` reference) so the JSON format can express it too: `"formed_property": "lit"`.

> **Footgun**: never toggle a property that one of your pattern ingredients also matches on (e.g. a `StatePropertyIngredient` requiring `lit=false` while `formedProperty("lit")` sets it to true) - the structure would invalidate itself right after forming.

## Visuals & recipe browsers

### `model(ResourceLocation modelId)`
Associates a Master-Dummy render model: once formed, every part block becomes invisible except the core, which renders `modelId`'s default-state model in its place. Physics/hitboxes are unaffected. Requires part/controller blocks to extend `AbstractMultiblockPartBlock`/`AbstractMultiblockControllerBlock`. See [Advanced Features § Master-Dummy model](../Advanced-Features.md#master-dummy-model).

### `keepVisible(char... symbols)`
Symbols whose positions stay visible (not auto-hidden) when `.model(...)` is set - e.g. IO ports. The core is always kept visible automatically.

### `icon(ResourceLocation itemId)`
Item shown as this structure's icon in JEI/REI/EMI.

Every definition's display name (JEI/REI/EMI title) resolves automatically from `multiblock.<namespace>.<path>`, following the same convention as `block.<namespace>.<path>` for blocks - define that key in your lang file. There's no separate call needed; the key is derived straight from the mandatory id.

### `ghostOverlayDebug()`
Dev-only: while enabled, players see a chat debug line with the ghost overlay's render time whenever it's drawn for this structure. Not meant to ship enabled.

### `autoPlace()`
Opts into auto-placement: Ctrl+Right-click on the unformed core auto-places every missing pattern position the player has the matching item for (consuming items; skipped in creative). See [Advanced Features § Auto-place](../Advanced-Features.md#auto-place).

### `autoPlaceOverlay()`
When a player looks at the core block of an unformed structure while holding an item that matches one or more missing positions, those positions are highlighted ghost-overlay style without needing to trigger the regular ghost overlay first. Only meaningful combined with `autoPlace()` - the preview promises the block can actually be auto-placed there.

## Build

### `build()`
```java
public MultiblockDefinition build()
```
Validates, constructs, and **registers** the definition into `MultiblockRegistry`. Throws `IllegalStateException` if `id` is unset, or if there are no layers/`PatternProvider`/`shapeless()`. If block-level core validation or geometry constraints fail, an error is logged and the definition is **not registered** (but is still returned).

### `buildWithoutRegistering()`
Same validation and construction, but never touches `MultiblockRegistry`. Use for testing or definitions you intend to register through another mechanism.

## Minimal example

```java
MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("examplemod", "furnace_array"))
        .layer("BBB", "BOB", "BBB")
        .key('B', BlockIngredient.tag(BlockTags.LOGS))
        .key('O', BlockIngredient.of(Blocks.FURNACE))
        .core('O')
        .rotations(RotationMode.HORIZONTAL)
        .onFormed(ctx -> { /* ... */ })
        .build();
```

## See also

- [Core Concepts](../Core-Concepts.md)
- [MultiblockDefinition](MultiblockDefinition.md)
- [BlockIngredient](BlockIngredient.md)
- [MultiblockTier](MultiblockTier.md)
- [Callbacks & Events § WrenchInteractionEvent](Callbacks-And-Events.md#wrenchinteractionevent) - the in-place variant upgrade `variant(...)` enables via the wrench.
- [Advanced Features](../Advanced-Features.md)

[← Back to Home](../index.md)

# `MultiblockDefinition`

Package: `net.astronomy.multilib.api.definition`

The immutable, built representation of a structure. You never construct this directly - get one from [`MultiblockBuilder.build()`](MultiblockBuilder.md#build) (via `MultiLibAPI.define(id)...build()`).

## Identity & shape accessors

| Method | Returns |
|---|---|
| `getId()` | The structure's `ResourceLocation` |
| `getBlockMap()` | `Map<Character, BlockIngredient>` - the symbol table |
| `getLayers()` | `List<List<String>>` - top-to-bottom layer rows |
| `getLayerCount()` | Number of layers |
| `getRotationMode()` | The coarse `RotationMode` |
| `getAllowedRotations()` | `Set<AllowedRotation>` - granular rotation declarations, if any (takes precedence over `RotationMode` when non-empty). Honored by both `ShapedMatcher` and `FunctionalMatcher` (for `.pattern(...)`-based structures) - the latter mirrors the former's granular-transform search |
| `isRequireAirInEmptyPositions()` | Whether empty pattern cells must be air |
| `getPriority()` | Match-order priority. Ties are broken by `MultiblockRegistry` - JSON-defined definitions win over Java-defined ones, not registration order |

## Core & activation

| Method | Returns |
|---|---|
| `getActivationSymbol()` / `hasActivation()` | The activation symbol, or `'\0'` if unset |
| `getCoreSymbol()` / `hasCore()` | The core symbol, or `'\0'` if unset |
| `matchesCore(BlockState state)` | Whether `state` matches the core symbol's ingredient |
| `matchesActivationOrCore(BlockState state)` | Whether `state` matches either the activation or core symbol's ingredient |
| `getCandidateBlocks()` | `Set<Block>` - every concrete block any symbol's ingredient could match; used by `MultiblockRegistry` to index this definition |

## Formation & callbacks

| Method | Returns |
|---|---|
| `getFormationMode()` | The `FormationMode` |
| `getFormedCallbacks()` / `getBrokenCallbacks()` | `List<...Callback>` (multiple allowed) |
| `getTickCallback()` / `hasTickCallback()` | `Optional<MultiblockTickCallback>` |
| `getAmbientCallback()` / `hasAmbientCallback()` / `getAmbientIntervalTicks()` | Ambient callback + its interval |
| `getValidator()` | `Optional<MultiblockValidator>` |

## Shape sources & shapeless

| Method | Returns |
|---|---|
| `getPatternProvider()` | `Optional<PatternProvider>` (procedural shape source) |
| `getBoundingBox()` | `Vec3i` override for provider-backed definitions |
| `isShapeless()` | Whether this is a flood-fill structure |
| `getShapelessMinSize()` / `getShapelessMaxSize()` | Bounding-box constraints |
| `getShellIngredient()` / `getShellFaces()` | Shapeless shell requirement(s) |
| `getInteriorIngredient()` | Shapeless interior requirement |
| `getShapelessRequirements()` | `List<ShapelessRequirement>` - count constraints |

## Optional & free-form positions

| Method | Returns |
|---|---|
| `getOptionalSymbols()` / `isOptional(char)` | Symbols allowed to mismatch |
| `getOptionalLayerIndices()` | Indices of layers the matcher may try excluding |
| `getFreeBlocks()` / `isFreeBlock(char)` | `Map<Character, FreeBlockSpec>` |

## Geometry constraints

| Method | Returns |
|---|---|
| `getUniqueSymbols()` | Symbols required to occur exactly once |
| `getSurfaceOnlySymbols()` / `getFrameOnlySymbols()` / `getInsideOnlySymbols()` | Placement-constrained symbol sets |

## Wall sharing

| Method | Returns |
|---|---|
| `isWallSharingEnabled()` | Definition-level default, `false` unless set via `.wallSharing(true)` on the builder |
| `getSymbolWallSharingOverrides()` | Per-symbol overrides set via `.key(symbol, ingredient, mode)`/`.noWallSharing(...)` |
| `getWallSharingMode(char symbol)` | Resolves the **effective** mode for a symbol. The chain differs by symbol role: for the **core/activation** symbol - symbol override → block-level `BlockDefinition` override → falls back to `DISABLED` regardless of the definition-level default. For **ordinary** symbols - symbol override → `IWallSharable`/`MultiLibAPI` block-level registration (only consulted if there's exactly one candidate block) → falls back to `isWallSharingEnabled() ? ENABLED : DISABLED` (i.e. `DISABLED` unless the definition opted in). Note: ordinary symbols never consult the block-level `BlockDefinition` override - that step only applies to core/activation symbols. |

## Visuals & recipe browsers

| Method | Returns |
|---|---|
| `getModelId()` / `hasModel()` | Master-Dummy model id, if set |
| `getKeepVisibleSymbols()` | Symbols excluded from auto-hiding |
| `getIconItem()` | JEI/REI/EMI icon item id |
| `getNameTranslationKey()` | Resolved `multiblock.<namespace>.<path>` key, auto-derived from the id |
| `isGhostOverlayDebug()` | Dev-only render-time debug flag |
| `isAutoPlace()` | Whether Ctrl+Right-click auto-placement is enabled |

## Notes on validation

Two checks run at `.build()` time and can cause registration to be **skipped** (definition object is still returned, but `MultiblockRegistry` never learns about it):

1. **Core consistency**: if any block in the symbol map has a block-level `.core(id)` declaration (see [BlockDefinition](BlockDefinition.md)) that conflicts with (or auto-fills) this definition's `.core(char)`, mismatches are logged as errors.
2. **Geometry constraints**: `unique()`/`surfaceOnly()`/`frameOnly()`/`insideOnly()` are checked against the textual layers; violations are logged as errors.

Always check your log output after adding a new definition - a silently-unregistered structure that never matches is the most common integration mistake (see [FAQ & Troubleshooting](../FAQ-Troubleshooting.md)).

## See also

- [MultiblockBuilder](MultiblockBuilder.md) - how to construct one
- [Core Concepts](../Core-Concepts.md)
- [MultiblockInstance & Registry](MultiblockInstance-And-Registry.md)

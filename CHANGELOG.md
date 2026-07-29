# Changelog

All notable changes to MultiLib are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/) — `MAJOR.MINOR.PATCH`:

- **MAJOR** — a breaking change to the public API (anything under `net.astronomy.multilib.api`,
  plus JSON/KubeJS-facing shapes). Bump even for a small break.
- **MINOR** — a backwards-compatible addition (new class, new opt-in method, new premade).
- **PATCH** — a backwards-compatible fix, no behavior change from the consumer's point of view.

`[Unreleased]` collects everything merged since the last tagged release, categorized. When a release
is cut, its entries move under a new version heading and this section resets to empty.

## [Unreleased]

### Added

- Multiblock Assembly system (`api/assembly`, `core/assembly`): link several independent,
  already-formed multiblock instances into one logical machine (role multiplicities, a connection
  graph, bottom-up formation, break policies, aggregated capabilities/stats). JSON, KubeJS
  (`MultiblockEvents.assembly(...)`), `/multilib assembly` commands, and dev-tool export included.
  See [wiki: Multiblock Assembly](wiki/api-reference/Multiblock-Assembly.md).
- 8 premade geometric `PatternProvider`s: `ConeProvider`, `DomeProvider`, `HollowDomeProvider`,
  `RingProvider`, `TorusProvider`, `PrismProvider`, `CompositeProvider` (CSG-style
  union/subtract/intersect composition of other providers), and `RevolutionProvider` (solid of
  revolution: sweeps an arbitrary cross-section provider around the Y axis between a min/max
  radius, for donut/torus shapes with a non-circular tube profile). Registered in JSON as
  `multilib:revolution` with a nested `cross_section` field. A new `example/donut` demo shows it
  off, including the technique for anchoring a single, non-repeated core block on an otherwise
  rotationally symmetric ring via `CompositeProvider`.
- 9 premade HUD providers: `ItemHudProvider`, `ControllerLocationHudProvider`,
  `StructureSizeHudProvider`, `AggregateGroupHudProvider`, `ErrorReasonHudProvider`,
  `RecipeHudProvider`, `StatHudProvider`, `PortsSummaryHudProvider`, `ComparatorOutputHudProvider`,
  plus assembly-level `AssemblyStatusProvider`/`AssemblyAggregateStatHudProvider`.
- Two new opt-in HUD source hooks: `HudErrorSource`, `HudComparatorSource` (same pattern as the
  existing `HudProcessSource`/`HudOwnershipSource`/`HudRedstoneSource`).
- `ProcessRecipe#getDisplayName()` default method, so `RecipeHudProvider` can show what job is
  currently running (backwards-compatible: defaults to empty).
- Jade tooltip now shows the multiblock's own display name instead of the looked-at block's name,
  for any block that's part of a **formed** instance.
- A hosted Maven repository at `https://astronomy20.github.io/MultiLib/maven/`, republished on every
  push to `1.21.1` (independent of CurseForge/Modrinth release cadence) — see
  [wiki: Maven Releases](wiki/Maven-Releases.md) for `jarJar` embedding coordinates.

### Changed

- **Breaking:** HUD providers are no longer registered by default. `FormedStatusProvider` was
  previously auto-registered globally; every provider (including it) is now opt-in via
  `MultiblockHudRegistry.registerGlobal(...)`/`.register(...)`. A consumer relying on the default
  "name + Formed" line appearing without registering anything must now register
  `FormedStatusProvider` explicitly.
- `FormedStatusProvider` shows the core's **live** `MultiblockState` (Idle/Running/Error/custom)
  instead of a fixed "Formed" text, when the core extends `AbstractMultiblockControllerBE`.
- `example/` reorganized into per-topic subpackages (`basic`, `directional`, `variants`, `tank`,
  `assembly`, `hud`) — internal to the excluded `example/**` source set, no consumer-facing impact.

### Fixed

- Jade's multiblock name override now respects `MultiblockHudRegistry.isHudEnabled(...)` (previously
  bypassed the per-definition killswitch).
- Removed leftover `TEMP DEBUG` logging left in `BlockActivationHandler`/`BlockBreakHandler` from a
  past investigation.
- `.pattern(PatternProvider)`-based definitions with a `.core(...)`/`.activation(...)` symbol failed
  `MultiblockBuilder` validation unconditionally and were silently never registered (core/activation
  presence was checked against the `.layer(...)`-only grid, which a procedural pattern never
  populates) — this also meant such definitions never showed up in the ghost overlay or JEI/REI/EMI,
  since both read from the registry. `validateUniqueCore`/`validateCoreActivationInPattern` now skip
  when there's no static grid to check, the same way `validateLayerDimensions` already did.
- Autoplace (`.autoPlace()`/`.autoPlaceOverlay()`) now works for `.pattern(PatternProvider)`-based
  definitions too: `AutoPlaceRequestHandler`/`AutoPlacePreviewRequestHandler` read
  `getPreviewLayers()`/`getPreviewBlockMap()` (an exact sample of the provider) instead of the
  always-empty `getLayers()`/`getBlockMap()`, matching what the ghost overlay already did.
- `StructureOrientation.detectFromPlacedBlocks` — the "orient the ghost overlay/autoplace preview to
  whatever's already physically built" check shared by the ghost overlay, autoplace, and
  `MultiblockProgressAPI` — read raw `getLayers()`/`getBlockMap()` too, so it silently detected
  nothing for any `.pattern(PatternProvider)`-based (or shapeless) definition, regardless of how much
  of the structure existed in the world. Both callers would then fall back to a live/guessed
  orientation instead of the real one, which could look like "autoplace only works once the ghost
  overlay has been opened" (the overlay's one-shot guess, once pinned, is at least stable - the live
  fallback isn't). Fixed the same way, with `getPreviewLayers()`/`getPreviewBlockMap()`; a no-op for
  every shaped `.layer()` definition (same object references in that case).
- Ghost overlay/autoplace's "orient to face the player" fallback (`StructureOrientation.orientationForFace`,
  used whenever nothing's placed yet to detect ground-truth orientation from) rigidly assumed every
  definition's body extends along its declared local +Z (row) axis relative to the core/activation
  symbol, rotating that axis to face the player. A definition anchored off-center along local X instead
  (or diagonally) previewed to the player's side instead of behind them. Added
  `StructureOrientation#orientationForFacing`, which measures the pattern's real core-to-body direction
  (from `getPreviewLayers()`/`getPreviewBlockMap()`, so it works for any provider) and picks whichever
  rotation aligns it closest to the player's facing; `OverlayRequestHandler`'s player-facing branch and
  `AutoPlaceRequestHandler`'s live-facing fallback both use it now. Reproduces the exact old
  SOUTH/WEST/NORTH/EAST mapping for the common Z-anchored case, so no behavior change there.

## Released versions

Tagged releases before this file existed aren't backfilled with categorized entries — see the git
tags/GitHub releases for their diffs.

- `multilib-1.1.0` — 2026-07-10
- `multilib-1.0.1` — 2026-07-09
- `multilib-1.0.0` — 2026-07-08

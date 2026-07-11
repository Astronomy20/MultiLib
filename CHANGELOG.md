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
- 7 premade geometric `PatternProvider`s: `ConeProvider`, `DomeProvider`, `HollowDomeProvider`,
  `RingProvider`, `TorusProvider`, `PrismProvider`, and `CompositeProvider` (CSG-style
  union/subtract/intersect composition of other providers).
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

## Released versions

Tagged releases before this file existed aren't backfilled with categorized entries — see the git
tags/GitHub releases for their diffs.

- `multilib-1.1.0` — 2026-07-10
- `multilib-1.0.1` — 2026-07-09
- `multilib-1.0.0` — 2026-07-08

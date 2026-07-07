[← Back to Home](index.md)

# Dev Tools

MultiLib ships an in-game authoring aid - the **Multiblock Dev Block** and its companion **Dev Wrench** - that lets you build a structure by hand in the world, scan it, tag its core/activation block, and export the result as a Java scaffold, a KubeJS script, or a datapack JSON definition. It's a shortcut for the "type out a layer grid by hand" step of [Core Concepts](Core-Concepts.md#definitions) and [JSON/datapack definitions](Advanced-Features.md#jsondatapack-definitions), not a replacement for filling in behavior (formation mode, validators, callbacks) yourself.

Only the **static geometry** (layers, symbol keys, core/activation) is captured - formation mode, shell/interior, validators, callbacks and every other behavioral option still need to be added by hand afterward. Every generated file carries a `TODO` comment saying so.

## Enabling dev mode

Everything in this page is gated behind `CommonConfig.DEV_MODE` (`devMode` in `config/multilib/common.toml`), off by default:

```toml
devMode = true
```

With `devMode = false`, the Multiblock Dev Block, its item, its block entity type and its menu type are **never registered at all** - not merely hidden - so a production build of a host mod carries no trace of them. Two things to keep in mind:

- If a world is saved with the dev block placed and `devMode` is later turned off, that position becomes an unknown/missing block on next load - the tool is meant to be used transiently (place, scan, export, break), not left down long-term.
- `devMode` isn't synced between client and server - every side of a shared dedicated server must use the same value, or the mismatched registries will cause NeoForge to reject/degrade the connection.

When enabled, the **Multiblock Dev Block** and **Multiblock Dev Wrench** both appear in the Tools & Utilities creative tab.

## The dev block

Placing a Multiblock Dev Block and right-clicking it (empty-handed or with any item other than the dev wrench) opens its GUI - a Structure-Block-style workflow. The block entity persists everything you configure, so reopening the GUI (or rejoining the world) resumes exactly where you left off:

- **Path** - the structure's `ResourceLocation` path (e.g. `fusion_reactor`); this is also what names every exported file.
- **Display Name (optional)** - cosmetic text written as the translation value; leave it blank and the path itself is used instead.
- **Offset X/Y/Z** - where the scanned area starts, relative to the dev block (defaults to `(0, 1, 0)`, i.e. one block above the dev block itself, matching how the tool is used in practice).
- **Size X/Y/Z** - the scanned area's dimensions.

The dev block's own position is always treated as air during a scan, even if your offset/size happens to overlap it.

### Scanning

Scanning isn't a one-shot button - it's driven entirely by the **auto-detect** toggle described below (the "Detect" button doubles as that toggle). A scan reads every block state in the configured area and turns it into a layer-grid pattern (top layer first, same row/column convention as `.layer(...)`), assigning one symbol per distinct block type in first-appearance order from an alphabet of 52 (`A`-`Z` then `a`-`z`) - deterministic, so re-scanning an unchanged area never reshuffles the symbols. The fully-empty border around the occupied blocks is trimmed automatically.

A scan can fail with one of three reasons, each reported with its own message:

- **Empty area** - no non-air blocks found.
- **Too many distinct block types** - more than 52 in the scanned area.
- **Incomplete multi-part block** - a door, bed, or tall plant (two-cell blocks) has only one of its two halves inside the area; resize the area to include the other half rather than have it silently exported broken.

## Auto-detect mode

The **Detect** button toggles auto-detect on/off (its label reflects the current state) rather than running a single scan. While on, the server periodically re-scans the area on its own - driven by `MultiblockTickHandler`, which walks every dev block currently registered as "auto-detect on" once every `CommonConfig.DEVTOOL_AUTO_DETECT_INTERVAL_TICKS` ticks (default `10`, i.e. twice a second). This keeps the scan, the tag, and the HUD list (see below) fresh as you add or remove blocks, without needing to reopen the GUI. Auto-detect is persisted per dev block and re-registers itself automatically on world reload/rejoin if it was left on.

## Render (area preview)

The **Render** button toggles a client-side translucent box outlining the currently configured offset/size area, updating live as you edit the offset/size fields. Like auto-detect, this is persisted on the block entity, so the preview box reappears on its own after closing the GUI, relogging, or restarting the world.

## Show List (HUD overlay)

The **Show List** button toggles a compact, scoreboard-sidebar-style HUD in the corner of the screen, listing the dev block's last scan: the tagged core or activation block (if any), then every distinct block type found with its occurrence count. It's meant to let you watch a structure's scan update live (especially alongside auto-detect) without keeping the GUI open. Only one dev block's list is shown per player at a time.

## Tagging core/activation blocks

Right-clicking a block with the **Multiblock Dev Wrench** (instead of any other item/empty hand) tags that block's type as the structure's core or activation symbol, following the same core-vs-activation rule used elsewhere in MultiLib: a block type that occurs exactly once in the area becomes the **core**; one that occurs more than once becomes the **activation** symbol instead (duplicates can't be a unique core). Right-clicking a block of the currently-tagged type again removes the tag.

A few notes on how tagging behaves:

- Holding the wrench suppresses every other interaction on the clicked block (GUI opening, item use, etc.) - a wrench click is always a tag attempt, nothing else.
- Tagging works as soon as the dev block's GUI has been opened once, even before any scan has run - occurrences are counted directly against the live world within the configured area, not against a cached scan.
- Once a scan exists, the clicked block must already be part of that scan (re-run Detect first if you've added a new block type since the last scan).
- Multi-part blocks (doors, beds, tall plants) count as **one** logical occurrence per instance, not one per half, so a two-cell door can still correctly become a unique core.
- Tagging is independent of the current scan: it identifies a block *type*, and each new scan re-resolves that type against the fresh grid to populate the exported core/activation symbol.

## Exporting a scanned structure

Once a scan has produced a result, the three **Export** buttons write it to disk in the corresponding format. Every export shares the same fixed namespace, `CommonConfig.DEVTOOL_NAMESPACE` (default `multilib`) - the GUI's Path field only ever supplies the *path* half of the resulting `namespace:path` id and translation key (`multiblock.<namespace>.<path>`).

| Format | Written to | Notes |
|---|---|---|
| **Export: Java** | `config/multilib/output/<ClassName>.java` (or `CommonConfig.DEVTOOL_JAVA_OUTPUT_DIR` if set) | A `MultiLibAPI.define(...)` scaffold with `.layer(...)`/`.key(...)`/`.core(...)`/`.name(...)` already filled in and `TODO` markers for everything else (package name, formation mode, callbacks, etc.) - meant to be copied into your own mod project. |
| **Export: KubeJS** | `kubejs/server_scripts/<namespace>/<path>.js` (or `CommonConfig.DEVTOOL_KUBEJS_OUTPUT_DIR` if set) | A `MultiblockEvents.create(...)` script using `MultiblockUtils.block(...)` for keys - see [KubeJS Integration](KubeJS-Integration.md). |
| **Export: JSON** | The current world's own `datapacks/<namespace>/data/<namespace>/multiblocks/<path>.json` (or `CommonConfig.DEVTOOL_JSON_OUTPUT_DIR` if set) | A real datapack definition in the [JSON/datapack format](Advanced-Features.md#jsondatapack-definitions) - the tool auto-creates the datapack's `pack.mcmeta` scaffold if missing, and automatically enables and reloads it (equivalent to `/datapack enable` + `/reload`), so the exported structure is detectable immediately with no manual step. |

Every format also writes the Display Name text to a lang file so the exported structure has a real translated name:

- Java/KubeJS entries are merged into a lang file alongside their own output tree (`assets/<namespace>/lang/en_us.json` under the Java output dir, or `kubejs/assets/<namespace>/lang/en_us.json` for KubeJS - KubeJS reads its lang/assets from that one fixed location regardless of the script's own subfolder).
- The JSON export's lang entry goes to a separate real client resourcepack at `<gamedir>/resourcepacks/<namespace>/assets/<namespace>/lang/en_us.json` (a datapack alone can't carry a translation - lang is resourcepack-only) - enable that resourcepack and reload resources to see the translated name in-game.
- Every export also gets a small standalone `<output file>.lang.json` snippet next to it, holding just that one entry, for an easy copy-paste into your own mod's real lang file.

Re-exporting the same `namespace:path` id always overwrites its own previous file without asking. If the target file already exists but was last written for a *different* id (a path collision), the export is refused with a confirmation prompt instead of silently overwriting someone else's file - exporting again after that confirms the overwrite.

## Loading / round-tripping exports

The GUI's **Load** tab lists every multiblock the tool can find: its own file exports across all three formats, plus every multiblock currently *registered* in the game - hardcoded Java from any mod, JSON datapacks, and KubeJS alike - not just ones this tool produced itself. If the same id shows up in both places, the live registered definition wins, since it reflects what's actually running rather than whatever was last written to disk. Shapeless and `PatternProvider`-based definitions are skipped, since neither can be represented as a static layer grid.

Loading an entry:

1. Re-populates the dev block's Path/Display Name fields and its scan data, as if that exact structure had just been scanned.
2. Resizes the dev block's area to the loaded pattern's own dimensions, clears that area to air, and stamps the pattern's blocks back into the world at the dev block's configured offset (re-linking any multi-part blocks like doors so they aren't left as broken unlinked halves).
3. Re-tags the loaded core/activation block for the wrench glow, if the original export had one tagged.
4. Turns the Render preview on, so you immediately see the loaded structure outlined and placed in the world, ready to tweak by hand and re-Detect.

This makes the dev block usable as a quick "open an existing multiblock, tweak it in the world, re-export" loop, not just a one-way scan-and-export tool.

## Config reference

All of the following live in `CommonConfig` (`config/multilib/common.toml`):

| Option | Default | Purpose |
|---|---|---|
| `devMode` | `false` | Master switch for the whole dev-tool suite (this page) plus other debugging-facing feedback (the ghost overlay debug countdown, wrench state chat messages). |
| `devtoolNamespace` | `multilib` | The fixed namespace half of every export's id/translation key, and of the JSON export's datapack folder name and the KubeJS export's `server_scripts` subfolder name. |
| `devtoolJavaOutputDir` | `config/multilib/output` | Output directory for "Export: Java", relative to the game directory (or an absolute path). |
| `devtoolKubeJsOutputDir` | *(empty)* | Output directory for "Export: KubeJS"; left empty, `kubejs/server_scripts/<devtoolNamespace>` is used instead. |
| `devtoolJsonOutputDir` | *(empty)* | Base directory for "Export: JSON"; left empty, the current world's own `datapacks/<devtoolNamespace>` folder is used instead (auto-scaffolded if missing). |
| `devtoolAutoDetectIntervalTicks` | `10` | How often (in ticks) a dev block with auto-detect on gets re-scanned; 20 ticks = 1 real-world second. |

## See also

- [Core Concepts](Core-Concepts.md)
- [Pattern Design Guide](Pattern-Design-Guide.md)
- [Advanced Features § JSON/datapack definitions](Advanced-Features.md#jsondatapack-definitions)
- [KubeJS Integration](KubeJS-Integration.md)

[← Back to Home](index.md)

# Dev Tools

An in-game authoring aid: build a structure by hand, scan it, and export it as a Java scaffold, a KubeJS script, or a datapack JSON. It automates writing the layer grid by hand; it does not fill in behavior — formation mode, validators, and callbacks are still yours to add. Only the static geometry (layers, symbol keys, core/activation) is captured, and every exported file marks the rest with a `TODO`.

## Enabling

Set `devMode = true` in `config/multilib/common.toml` (default `false`). While off, the dev block, its item, block entity, and menu are never registered — a production build carries no trace of them.

- Break dev blocks before disabling `devMode`, or their saved positions load as missing blocks.
- `devMode` isn't synced. On a dedicated server, client and server must match, or NeoForge rejects the connection.

When enabled, the **Multiblock Dev Block**, **Dev Wrench**, and **[Preference Wrench](api-reference/Ambiguity-And-Preferences.md#preference-wrench-dev-mode)** appear in the Tools & Utilities creative tab. (The preference wrench is a separate tool for picking which definition an ambiguous block resolves to, not part of the scan/export flow below.)

## The dev block

Right-click it (empty-handed or holding anything but the dev wrench) to open its GUI. All fields persist on the block entity across GUI reopens and world reloads:

| Field | Meaning |
|---|---|
| **Path** | The structure's id path (e.g. `fusion_reactor`); also names every exported file. |
| **Display Name** | Optional translation text; blank falls back to the path. |
| **Offset X/Y/Z** | Scan-area start, relative to the dev block. Default `(0, 1, 0)` — one block above. |
| **Size X/Y/Z** | Scan-area dimensions. |

The dev block's own position always counts as air, even if the area overlaps it.

### Scanning

Scanning is driven by the **auto-detect** toggle, not a one-shot button. A scan reads the area into a layer grid (top layer first, same convention as `.layer(...)`), assigning one symbol per distinct block type in first-appearance order from a 52-symbol alphabet (`A`–`Z`, `a`–`z`). It's deterministic — an unchanged area always yields the same symbols — and the empty border is trimmed automatically.

Three failure cases, each with its own message:

| Failure | Cause |
|---|---|
| **Empty area** | No non-air blocks. |
| **Too many block types** | More than 52 distinct blocks. |
| **Incomplete multi-part block** | A door/bed/tall plant has only one half inside the area. Resize to include both. |

### Auto-detect

The **Detect** button toggles auto-detect (its label shows the state). While on, the server re-scans the area every `devtoolAutoDetectIntervalTicks` ticks (default `10`), keeping the scan, tag, and HUD list current as you edit blocks. It persists per block and re-registers on world reload.

### Render

**Render** toggles a translucent box outlining the configured area, updating live with the offset/size fields. Persisted, so it reappears after reload.

### Show List

**Show List** toggles a scoreboard-style HUD listing the last scan: the tagged core/activation block, then each distinct block with its count. One list per player at a time. Useful for watching a scan update live alongside auto-detect.

## Tagging core/activation

Right-click a block with the **Dev Wrench** to tag its type. The standard rule applies: a block occurring once becomes the **core**; one occurring more than once becomes the **activation** symbol. Right-clicking a tagged type again clears it.

- Holding the wrench suppresses all other interaction on the block — a wrench click is only ever a tag attempt.
- Tagging works as soon as the GUI has been opened once, before any scan; occurrences are counted against the live world in the area.
- Once a scan exists, the block must already be part of it — re-Detect first if you've added a new block type.
- Multi-part blocks (doors, beds, tall plants) count as one occurrence, so a two-cell door can still be a unique core.
- Tagging targets a block *type*; each scan re-resolves it against the fresh grid.

## Exporting

Once a scan exists, the three **Export** buttons write it to disk. Every export uses the fixed namespace `devtoolNamespace` (default `multilib`); the Path field supplies only the path half of the `namespace:path` id and its `multiblock.<namespace>.<path>` translation key.

| Format | Written to | Result |
|---|---|---|
| **Java** | `config/multilib/output/<ClassName>.java` | A `MultiLibAPI.define(...)` scaffold with layers/keys/core/name filled in, `TODO`s for the rest. Copy into your mod. |
| **KubeJS** | `kubejs/server_scripts/<namespace>/<path>.js` | A `MultiblockEvents.create(...)` script — see [KubeJS Integration](KubeJS-Integration.md). |
| **JSON** | The world's `datapacks/<namespace>/.../multiblocks/<path>.json` | A real [datapack definition](Advanced-Features.md#jsondatapack-definitions). The tool scaffolds `pack.mcmeta`, then enables and reloads the pack, so the structure is detectable immediately. |

Each export also writes the Display Name to a lang file (the JSON export uses a client resourcepack under `resourcepacks/<namespace>/`, since datapacks can't carry translations), plus a standalone `<file>.lang.json` snippet beside the output for copy-paste.

Re-exporting the same id overwrites without asking. A path collision with a *different* id is refused with a confirmation prompt; export again to overwrite.

Output directories are overridable via `devtoolJavaOutputDir` / `devtoolKubeJsOutputDir` / `devtoolJsonOutputDir`.

## Loading

The **Load** tab lists every multiblock the tool can find — its own exports in all three formats, plus every multiblock currently registered (Java, JSON, KubeJS). When an id appears in both, the live registered definition wins. Shapeless and `PatternProvider` definitions are skipped (no static grid).

Loading an entry:

1. Restores the Path/Display Name fields and scan data.
2. Resizes the area to the pattern, clears it, and stamps the blocks into the world (re-linking multi-part blocks).
3. Re-tags the core/activation block if the export had one.
4. Turns on Render and auto-detect, so the structure appears placed and stays scanned as you edit it.

This makes the block a round-trip loop — open an existing multiblock, tweak it in-world, re-export — not just one-way.

## Config reference

All under `CommonConfig` (`config/multilib/common.toml`):

| Option | Default | Purpose |
|---|---|---|
| `devMode` | `false` | Master switch for the whole dev-tool suite and other debug feedback (ghost-overlay countdown, wrench state chat). |
| `devtoolNamespace` | `multilib` | Namespace half of every export's id and translation key. |
| `devtoolJavaOutputDir` | `config/multilib/output` | Java export directory. |
| `devtoolKubeJsOutputDir` | *(empty)* | KubeJS export directory; empty → `kubejs/server_scripts/<namespace>`. |
| `devtoolJsonOutputDir` | *(empty)* | JSON export base; empty → the world's own `datapacks/<namespace>`. |
| `devtoolAutoDetectIntervalTicks` | `10` | Auto-detect re-scan interval in ticks (20 = 1 second). |

## See also

- [Core Concepts](Core-Concepts.md)
- [Configuration](Configuration.md) — the full `devMode`/`devtool*` option list plus the runtime knobs
- [Ambiguity & Preferences](api-reference/Ambiguity-And-Preferences.md) — the preference wrench
- [Advanced Features § JSON/datapack definitions](Advanced-Features.md#jsondatapack-definitions)
- [KubeJS Integration](KubeJS-Integration.md)

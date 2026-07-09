[← Back to Home](index.md)

# Configuration

Every runtime knob MultiLib exposes, in two config files NeoForge generates on first launch. All values have safe defaults — a mod integrating MultiLib works out of the box without touching either file.

Both files live under a **`config/multilib/`** subfolder (MultiLib registers them explicitly, rather than using NeoForge's default flat `config/multilib-*.toml`):

- **`config/multilib/common.toml`** — [`CommonConfig`](#commonconfig-configmultilibcommontoml), registered as `COMMON` (shared by client and server). On a dedicated server this lives in the server's config; a few values (`devMode`) must match between client and server or the connection is rejected.
- **`config/multilib/client.toml`** — [`ClientConfig`](#clientconfig-configmultilibclienttoml), registered as `CLIENT`, so it stays local to each player's install and never syncs to a server.

## `CommonConfig` (`config/multilib/common.toml`)

| Option | Type | Default | Range | Purpose |
|---|---|---|---|---|
| `ghostOverlayDurationSeconds` | int | `10` | 1–3600 | How long a [ghost-overlay](Advanced-Features.md#ghost-overlay) session stays active before the server sends a disable packet. |
| `autoPlaceSpeedHeldItem` | double | `1.25` | 0.1–10.0 | [Auto-place](Advanced-Features.md#auto-place) repeat speed with an item in hand, as a multiplier of vanilla's right-click repeat rate (`1.0` = vanilla, `2.0` = twice as fast). |
| `autoPlaceSpeedEmptyHand` | double | `1.25` | 0.1–10.0 | Auto-place repeat speed with an empty hand, same multiplier scale. |
| `devMode` | bool | `false` | — | Master switch: registers the whole [Dev Tools](Dev-Tools.md) suite and the [preference wrench](api-reference/Ambiguity-And-Preferences.md#preference-wrench-dev-mode), and enables debugging chat feedback (the ghost-overlay countdown and wrench state messages). Off by default because that feedback is developer output, not player-facing. **Must match between client and server** — it isn't synced, so a mismatch makes NeoForge reject the connection. |
| `devtoolNamespace` | string | `multilib` | — | The fixed `namespace` half shared by every Dev Block export's id, translation key, and generated folder name. See [Dev Tools § Exporting](Dev-Tools.md#exporting). |
| `devtoolJavaOutputDir` | string | `config/multilib/output` | — | Directory for the Dev Block's **Export: Java** button, relative to the game dir (or absolute). |
| `devtoolKubeJsOutputDir` | string | *(empty)* | — | Directory for **Export: KubeJS**. Empty → `kubejs/server_scripts/<devtoolNamespace>`. |
| `devtoolJsonOutputDir` | string | *(empty)* | — | Base directory for **Export: JSON** (`data/<namespace>/multiblocks/<path>.json` is created under it). Empty → the current world's own `datapacks/<devtoolNamespace>`. |
| `devtoolAutoDetectIntervalTicks` | int | `10` | 1–1200 | How often (in ticks; 20 = 1 second) a Dev Block with [auto-detect](Dev-Tools.md#auto-detect) re-scans its area. |

The `devtool*` and `devMode` options only matter while authoring with the Dev Tools — see that page for the full workflow. The first three (`ghostOverlay*`, `autoPlace*`) tune player-facing preview/build feel and apply whenever those features are used.

## `ClientConfig` (`config/multilib/client.toml`)

| Option | Type | Default | Purpose |
|---|---|---|---|
| `jeiPreviewAutoRotate` | bool | `true` | Whether the recipe browser's standalone 3D preview model auto-rotates by default. Only affects the JEI/REI/EMI recipe page's rotating model — the in-world ghost overlay is anchored to the structure's real placement and never rotates. |
| `categoryIcon` | string | *(empty)* | Item id used as the icon for the multiblock category tab in JEI/REI/EMI (e.g. `minecraft:crafting_table`). When set, it overrides whatever a consuming mod registered via [`MultiLibClient.setCategoryIcon(...)`](api-reference/MultiLibClient.md#setcategoryiconitemlike-item); leave empty unless a player/dev wants to force a specific icon. |

## See also

- [Dev Tools](Dev-Tools.md) — the `devMode`/`devtool*` options in context
- [Advanced Features § Ghost overlay](Advanced-Features.md#ghost-overlay), [§ Auto-place](Advanced-Features.md#auto-place)
- [Ambiguity & Preferences](api-reference/Ambiguity-And-Preferences.md) — the preference wrench is gated on `devMode`

[← Back to Home](../index.md)

# HUD Providers (Jade / The One Probe)

Packages: `net.astronomy.multilib.api.hud` (the neutral API + built-in providers), `net.astronomy.multilib.compat.jade`, `net.astronomy.multilib.compat.top` (viewer adapters)

Write hover-info once, and MultiLib shows it on whichever "what am I looking at" viewer the player has installed - Jade and The One Probe are supported out of the box, both as **optional** dependencies auto-detected at runtime (no config, nothing to enable). Providers run **server-side**; entries travel to the client serialized inside the viewers' own data channels, so there are no custom packets.

**Everything is opt-in.** `MultiblockHudRegistry` starts completely empty - a definition with nothing registered for it shows nothing on Jade/TOP, not even its name. Every built-in provider (including the "show the definition's name" one) is a ready-made class you register yourself; none of them wire up automatically. Same "mechanism only, UX is the dev's call" policy as the rest of this library.

## The entry model - `HudEntry`

```java
public sealed interface HudEntry {
    record Text(Component text) implements HudEntry {}
    record Progress(float fraction, Component label) implements HudEntry {}
    record KeyValue(Component key, Component value) implements HudEntry {}
}
```

Typed rather than string-only so each viewer renders natively: `Progress` becomes a real Jade progress bar / TOP progress element, not a text line. Each entry NBT round-trips via `save(CompoundTag, HolderLookup.Provider)` / `load(...)` - relevant only if you write your own viewer adapter.

## `HudContext` and `MultiblockHudProvider`

```java
public record HudContext(ServerLevel level, BlockPos pos, MultiblockInstance instance,
                         MultiblockDefinition definition, ServerPlayer player) {
    public static Optional<HudContext> at(ServerLevel level, BlockPos pos, ServerPlayer player);
}

@FunctionalInterface
public interface MultiblockHudProvider {
    void appendHudEntries(HudContext ctx, Consumer<HudEntry> out);
}
```

`HudContext.at` resolves the formed instance at a position (empty if none). A provider just appends entries:

```java
MultiblockHudRegistry.register(MY_DEFINITION_ID, (ctx, out) -> {
    out.accept(new HudEntry.KeyValue(
            Component.literal("Heat"),
            Component.literal(getHeat(ctx) + " C")));
});
```

## `MultiblockHudRegistry`

```java
public final class MultiblockHudRegistry {
    public static void registerGlobal(MultiblockHudProvider provider);
    public static void register(ResourceLocation definitionId, MultiblockHudProvider provider);
    public static void setHudEnabled(ResourceLocation definitionId, boolean enabled);
    public static boolean isHudEnabled(ResourceLocation definitionId);
    public static void setUnformedHintsEnabled(boolean enabled);
    public static boolean isUnformedHintsEnabled();
    public static List<HudEntry> gatherEntries(HudContext ctx);
    public static List<HudEntry> gatherUnformedEntries(ServerLevel level, BlockPos pos, ServerPlayer player);
}
```

- The registry starts **empty** - global providers run for every formed multiblock, per-definition providers only for theirs, in registration order, but only once you register something.
- `setHudEnabled(id, false)` is a killswitch on top of that: **nothing** is shown for that definition regardless of what's registered for it - including `compat/jade`'s name-override (below).
- Each provider runs inside its own try/catch (logged, skipped on throw) - one buggy provider can't blank the whole overlay.
- Register during mod init; gathering happens on the server thread.

## Built-in providers

Nineteen ready-made providers, grouped by where their data comes from. All are opt-in - register the ones you want.

### Straight from the instance

| Provider | Shows | Notes |
|---|---|---|
| `FormedStatusProvider` | Definition display name, then a status line - the resolved core's **live** `MultiblockState` name (Idle/Running/Error/your own) if it extends `AbstractMultiblockControllerBE`, else a generic "Formed" | The simple, general-purpose one; register globally for the classic "name + status" line |
| `TierHudProvider` | Resolved tier name per tiered symbol | Only useful on a definition with `.tier(...)` declarations |
| `StatHudProvider` | One `.tierStats(...)` stat, generic/parametrized | `new StatHudProvider(key, label, merger, identity)` or `StatHudProvider.summing(key, label)` - the merge rule across symbols is always explicit |
| `ControllerLocationHudProvider` | The core's coordinates + straight-line distance, when looking at a non-core member | Nothing shown when looking at the core itself, or the definition has no core |
| `StructureSizeHudProvider` | Current block count | Most useful on shapeless/variable-size definitions |
| `PortsSummaryHudProvider` | One line per distinct [port](Ports.md) block attached ("Energy Hatch: 2") | Groups by the port block's own name - no extra wiring needed |
| `MissingBlocksProvider` | For **unformed** structures whose core you're looking at: "X of Y blocks" progress | Global switch: `MultiblockHudRegistry.setUnformedHintsEnabled(true)` |
| `AggregateGroupHudProvider` | Size of the [aggregation](Block-Aggregation.md) group the looked-at member belongs to | Only fires when that member's own BE ALSO implements `AggregatableBlockEntity` on top of being a pattern-matched member - a pure aggregating block with no `MultiblockDefinition` at all is outside `api/hud`'s scope, since it's keyed off a formed `MultiblockInstance` |

### Straight from a NeoForge capability at the core

| Provider | Shows |
|---|---|
| `EnergyHudProvider` | "Energy: X / Y FE" from the controller's energy capability |
| `FluidHudProvider` | Tank contents ("Fluid"/"Tank N" per tank) from the controller's fluid capability |
| `ItemHudProvider` | "Items: N / M slots" summary from the controller's item capability - a count, not one line per stack |

All three work with any capability-exposing controller, e.g. [components](Components.md) wired up via `MultiblockComponentHelper`.

### Via an opt-in `HudXxxSource` hook

MultiLib can't know your block entity's fields; implementing a one-method interface on the controller BE is how these providers find them.

| Provider | Hook interface | Shows |
|---|---|---|
| `ProcessHudProvider` | `HudProcessSource { Optional<RecipeProcessor<?, ?>> getHudProcessor(); }` | Progress bar of a [`RecipeProcessor`](Process-Engine.md) |
| `RecipeHudProvider` | Same `HudProcessSource` | WHAT is running, via `ProcessRecipe#getDisplayName()` - complements `ProcessHudProvider`'s bar |
| `RedstoneControlHudProvider` | `HudRedstoneSource { RedstoneControlComponent getHudRedstoneControl(); }` | Current [`RedstoneMode`](Control-And-Commands.md#redstonecontrolcomponent) |
| `OwnershipHudProvider` | `HudOwnershipSource { OwnershipComponent getHudOwnership(); }` | Owner name (profile-cache lookup, UUID fallback) |
| `ErrorReasonHudProvider` | `HudErrorSource { Optional<Component> getHudErrorReason(); }` | Why a structure is erroring - MultiLib has no built-in "error reason" (a failed `MultiblockValidator` blocks formation outright, it doesn't leave a formed structure with a reason attached), so this is entirely dev-supplied |
| `ComparatorOutputHudProvider` | `HudComparatorSource { int getHudComparatorOutput(); }` | Current comparator level (0-15), without needing to place a comparator to check |

```java
public interface HudProcessSource {
    Optional<RecipeProcessor<?, ?>> getHudProcessor();
}
```

### Assembly-level ([Multiblock Assembly](Multiblock-Assembly.md))

| Provider | Shows |
|---|---|
| `AssemblyStatusProvider` | The assembly's state + a per-role member count, on any member that belongs to one |
| `AssemblyAggregateStatHudProvider` | The actual `aggregateStat` numbers (e.g. "power: 480") computed via `AssemblyStatAggregator` |

## Viewer adapters

- **Jade** (`compat/jade`, Jade 15.x): an `@WailaPlugin` registering a server-data provider (runs the registry gather, writes serialized entries) and a client component provider rendering them - `Text` as tooltip lines, `Progress` via Jade's progress element, `KeyValue` as "key: value".
  - **Name override**: when the looked-at block is part of a **formed** instance (and HUD isn't disabled for that definition via `setHudEnabled`), Jade's own object-name line - normally the block's own name, e.g. "Gold Block" - is replaced with the multiblock's display name (`multiblock.<namespace>.<path>`), so the tooltip reads "Example Multiblock" instead of naming whichever block happens to be standing there. An unformed core/activation block still shows its own real name.
  - If a registered provider's `HudEntry.Text` line happens to resolve to the exact same text as that title (typically `FormedStatusProvider`'s own name line), it's silently deduplicated rather than shown twice - by resolved content, not by which provider produced it, so any provider gets the same treatment.
- **The One Probe** (`compat/top`): registered via InterModComms only when TOP is loaded; the guard class contains zero TOP imports, so nothing classloads without the mod. TOP providers already run server-side - entries map to `mcText`/`progress` elements. TOP has no title-override hook, so `FormedStatusProvider`'s own name line is what identifies the structure there.

Both are `compileOnly` dependencies in `build.gradle` (CurseMaven / k-4u maven) with optional entries in `neoforge.mods.toml`.

Three cosmetic details are flagged for in-game verification (not logic risks): Jade's progress-element style flag, TOP's progress prefix/suffix layout, and per-hover overhead on very busy servers (the no-instance early-return is a single map lookup).

## See also

- [Process Engine](Process-Engine.md), [Components](Components.md), [Control & Commands](Control-And-Commands.md), [MultiblockTier](MultiblockTier.md) - the features the built-in providers surface.
- [Multiblock States & Progress](Multiblock-States-And-Progress.md) - the progress API behind `MissingBlocksProvider`, and `MultiblockState` behind `FormedStatusProvider`'s live status line.
- [Ports (Hatches)](Ports.md) - what `PortsSummaryHudProvider` scans for.
- [Block Aggregation](Block-Aggregation.md) - the mechanism `AggregateGroupHudProvider` surfaces.
- [Multiblock Assembly](Multiblock-Assembly.md) - `AssemblyStatusProvider`/`AssemblyAggregateStatHudProvider`.

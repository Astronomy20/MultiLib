[← Back to Home](../index.md)

# HUD Providers (Jade / The One Probe)

Packages: `net.astronomy.multilib.api.hud` (the neutral API + built-in providers), `net.astronomy.multilib.compat.jade`, `net.astronomy.multilib.compat.top` (viewer adapters)

Write hover-info once, and MultiLib shows it on whichever "what am I looking at" viewer the player has installed - Jade and The One Probe are supported out of the box, both as **optional** dependencies auto-detected at runtime (no config, nothing to enable). Providers run **server-side**; entries travel to the client serialized inside the viewers' own data channels, so there are no custom packets.

This respects the library's no-forced-UX policy: only one line ("Formed" + definition name) is shown by default, everything else is a per-definition opt-in, and even the default can be switched off per definition.

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

- Global providers run for every formed multiblock; per-definition providers only for theirs, in registration order.
- `setHudEnabled(id, false)` is the killswitch: **nothing** is shown for that definition, built-ins included.
- Each provider runs inside its own try/catch (logged, skipped on throw) - one buggy provider can't blank the whole overlay.
- Register during mod init; gathering happens on the server thread.

## Built-in providers

Only the first is active by default; everything else is an explicit opt-in.

| Provider | Default | Shows | How to opt in |
|---|---|---|---|
| `FormedStatusProvider` | **on** (global) | Definition display name + formed status | Nothing to do; suppress per definition via `setHudEnabled(id, false)` |
| `TierHudProvider` | off | Resolved tier name per tiered symbol | `MultiblockHudRegistry.register(id, new TierHudProvider())` |
| `ProcessHudProvider` | off | Progress bar of a [`RecipeProcessor`](Process-Engine.md) | Implement `HudProcessSource` on your controller BE + register the provider |
| `EnergyHudProvider` | off | "Energy: X / Y FE" from the controller's energy capability | Register the provider (works with any cap-exposing controller, e.g. [components](Components.md)) |
| `FluidHudProvider` | off | Tank contents from the controller's fluid capability | Register the provider |
| `RedstoneControlHudProvider` | off | Current [`RedstoneMode`](Control-And-Commands.md#redstonecontrolcomponent) | Implement `HudRedstoneSource` + register |
| `OwnershipHudProvider` | off | Owner name (profile-cache lookup, UUID fallback) | Implement `HudOwnershipSource` + register |
| `MissingBlocksProvider` | off (global switch) | For **unformed** structures whose core block you're looking at: "X of Y blocks" progress | `MultiblockHudRegistry.setUnformedHintsEnabled(true)` |

The three opt-in source interfaces are one-method each, e.g.:

```java
public interface HudProcessSource {
    Optional<RecipeProcessor<?, ?>> getHudProcessor();
}
```

MultiLib can't know your BE's fields; implementing the interface on the controller BE is how a built-in provider finds them.

## Viewer adapters

- **Jade** (`compat/jade`, Jade 15.x): an `@WailaPlugin` registering a server-data provider (runs the registry gather, writes serialized entries) and a client component provider rendering them - `Text` as tooltip lines, `Progress` via Jade's progress element, `KeyValue` as "key: value".
- **The One Probe** (`compat/top`): registered via InterModComms only when TOP is loaded; the guard class contains zero TOP imports, so nothing classloads without the mod. TOP providers already run server-side - entries map to `mcText`/`progress` elements.
- Both are `compileOnly` dependencies in `build.gradle` (CurseMaven / k-4u maven) with optional entries in `neoforge.mods.toml`.

Three cosmetic details are flagged for in-game verification (not logic risks): Jade's progress-element style flag, TOP's progress prefix/suffix layout, and per-hover overhead on very busy servers (the no-instance early-return is a single map lookup).

## See also

- [Process Engine](Process-Engine.md), [Components](Components.md), [Control & Commands](Control-And-Commands.md), [MultiblockTier](MultiblockTier.md) - the features the built-in providers surface.
- [Multiblock States & Progress](Multiblock-States-And-Progress.md) - the progress API behind `MissingBlocksProvider`.

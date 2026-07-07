[← Back to Home](index.md)

# KubeJS Integration

MultiLib exposes a KubeJS event group (`MultiblockEvents`) letting scripters create and modify multiblock definitions from `.js` scripts, without touching Java. This is purely additive - normal KubeJS scripting keeps working unmodified whether or not MultiLib is present.

Optional and reflection-gated: if the `kubejs` mod isn't loaded, none of this activates and MultiLib behaves exactly as it does without KubeJS. If KubeJS *is* loaded but MultiLib was built without `kubejs_version` set in `gradle.properties`, the `compat.kubejs` package is excluded from the source set entirely - `MultiLib` catches the resulting `ClassNotFoundException` and simply skips setup.

## When scripts run

Both `MultiblockEvents.create(...)` and `MultiblockEvents.modify(...)` live in **`server_scripts`**, not `startup_scripts` - the same rule that governs JSON datapack multiblocks applies here: they re-fire on every `MultiblockDefinitionsReloadedEvent`, which covers both initial server/datapack load *and* every `/reload`, so iterating on a definition doesn't require restarting the game.

Firing order on each reload, all driven by `KubeJSMultiblockSetup`:

1. `MultiblockEvents.create(event => {...})` - every listener gets a chance to declare a `MultiblockBuilder` via `event.multiblock(id)`.
2. Every collected builder is built (`buildWithoutRegistering()`) and swapped into the registry via `MultiblockRegistry.replace(id, definition)` - a no-op remove followed by register, so the same script safely re-declares its own multiblock on every reload without an "already registered" error.
3. `MultiblockEvents.modify(event => {...})` - by this point every Java-, JSON-, and KubeJS-created definition is registered, so `event.modify(id, ...)` can patch any of them regardless of source.

Because `KubeJSMultiblockSetup.init()` is only invoked from `MultiLib`'s `FMLCommonSetupEvent` (not the earliest registry phase), and `MultiblockEvents.create`/`modify` don't run until the first `MultiblockDefinitionsReloadedEvent` after that, all block/item registries - including ones populated by KubeJS's own `StartupEvents.registry(...)` scripts - are guaranteed already closed. There's no ordering bug where a script's `key(symbol, block)` call resolves to a block that doesn't exist yet.

## Creating a multiblock

```js
MultiblockEvents.create(event => {
    event.multiblock('examplemod:my_altar')
        .name('my_altar')
        .layer('PPP', ' P ', ' G ')
        .layer('POP', ' P ', ' G ')
        .key('P', 'minecraft:stone_bricks')
        .key('O', 'examplemod:controller')
        .key('G', 'minecraft:gold_block')
        .core('O')
        .formationMode('automatic_and_wrench')
        .rotations('horizontal')
        .onFormed(ctx => {
            MultiblockUtils.playSound(ctx, 'minecraft:block.beacon.activate')
        })
})
```

`event.multiblock(id)` returns a normal `MultiblockBuilder` - the same fluent API documented in the [MultiblockBuilder reference](api-reference/MultiblockBuilder.md) is available from JS, so anything expressible in Java (shaped/shapeless/procedural patterns, callbacks, wall sharing, `.model(...)`, etc.) is expressible here too. **Don't call `.build()` yourself** - `KubeJSMultiblockSetup` collects every builder returned from `event.multiblock(...)` and registers each one for you (via `buildWithoutRegistering()` + `MultiblockRegistry.replace(...)`, not the builder's own `build()`/register path) once every `create` listener has run, so re-running the script on `/reload` doesn't throw "already registered".

The method is named `multiblock(...)` rather than `create(...)` deliberately: the same script file typically also has a `StartupEvents.registry('block', event => event.create(id))` block, where `create` on that event object means something unrelated - reusing the name across two different event objects would be confusing to read later.

If a validation failure occurs (e.g. a duplicate core symbol), it's reported both to the server log and to the KubeJS console/script error overlay with the reason from `MultiblockBuilder#getLastValidationError()`. If two different `create` listeners declare the same id in the same reload, the second one silently replaces the first - a warning is logged pointing at the duplicate id.

### Registering a wrench item

```js
MultiblockEvents.create(event => {
    event.wrench(Item.get('examplemod:my_wrench'))
})
```

This is the script-side equivalent of implementing `IMultiblockWrench` on a hand-written `Item` subclass - useful for items created entirely in KubeJS, which can't implement a custom Java interface. See [Advanced Features § Wrench tool](Advanced-Features.md#wrench-tool).

## Modifying an existing multiblock

`MultiblockEvents.modify` can patch **any** currently-registered definition - Java-registered, JSON-datapack, or previously KubeJS-created - by id:

```js
MultiblockEvents.modify(event => {
    event.modify('examplemod:my_altar', builder => {
        builder.key('G', 'minecraft:diamond_block')
        builder.onBroken(ctx => {
            MultiblockUtils.summon(ctx, 'minecraft:lightning_bolt')
        })
    })
})
```

`event.modify(id, callback)` returns `true` on success. It fails (returns `false` and logs to the KubeJS console) when:

- No multiblock is registered under `id` yet.
- The callback's changes fail validation when rebuilt (the console message includes the same `getLastValidationError()` reason as create-time failures).

Internally this snapshots the existing `MultiblockDefinition` via `MultiblockBuilder#toBuilder()`, hands your callback the resulting mutable builder, rebuilds it, and swaps it into `MultiblockRegistry` in place - the original definition (JSON or Java) is unaffected until your callback's rebuild succeeds.

Because everything flows through `toBuilder()`, newer builder features are scriptable for free: `modify` can call [`formedProperty(...)`](api-reference/MultiblockBuilder.md#formed-state-property) and the [tier stat-map overloads / `tierStats(...)`](api-reference/MultiblockBuilder.md#tiers) on an existing definition just like any other builder method.

## Wrench interaction events

```js
MultiblockEvents.wrench(event => {
    if (event.status === 'formation_failed') {
        event.player?.tell(`Couldn't form: ${event.failureReason}`)
    }
})
```

Fires on every wrench interaction (see [Advanced Features § Wrench tool](Advanced-Features.md#wrench-tool)), for **any** wrench item - whether it implements `IMultiblockWrench` in Java or was registered via `event.wrench(item)` above. MultiLib itself never shows the player anything on its own; a script wanting player-facing feedback listens here (see [[feedback-no-hardcoded-player-facing-behavior]] - the library is mechanism-only, UX is left to the mod/script author).

`event.status` is one of: `"not_a_multiblock"`, `"already_formed"`, `"mode_disallows_wrench"`, `"formed"`, `"formation_failed"`. `event.multiblockId` is `null` only when the status is `"not_a_multiblock"`; `event.failureReason` is only non-null when the status is `"formation_failed"`.

## `MultiblockUtils` helpers

A small set of script-friendly helpers, bound globally as `MultiblockUtils` - the JS-facing equivalent of what `MultiblockCodecs` is for the JSON format: translating a raw Java concept (a `SoundEvent`, an `EntityType`) into a plain string id, for use inside `onFormed`/`onBroken` callbacks:

| Method | Behavior |
|---|---|
| `MultiblockUtils.playSound(ctx, soundId)` / `playSound(ctx, soundId, volume, pitch)` | Plays a sound at the callback context's position (unknown sound id: warns and no-ops) |
| `MultiblockUtils.summon(ctx, entityId)` | Spawns an entity (e.g. `"minecraft:lightning_bolt"`) at the callback context's position (unknown/disabled entity type: warns and no-ops) |

Both take the shared `MultiblockEventContext` that `onFormed`/`onBroken` callbacks receive, rather than one overload per concrete context type - an earlier attempt overloaded on the two concrete context types directly, but Rhino's overload resolution couldn't disambiguate two unrelated types sharing an argument count.

## Limitations

- No script-facing equivalent yet for defining a completely new `PatternProvider` type from JS - the five built-in providers (sphere, hollow sphere, cylinder, hollow cube, pyramid) are usable via the builder, but a custom shape still requires a Java `PatternProviderSerializer`.
- `event.modify(...)` can only replace a definition wholesale via a rebuilt builder; there's no way to read individual fields off the existing definition from JS before deciding how to change them beyond what `MultiblockBuilder`'s own getters expose (e.g. `getLastValidationError()`).

## See also

- [Advanced Features](Advanced-Features.md) - JSON/datapack definitions, wrench tool, recipe-browser compatibility.
- [MultiblockBuilder reference](api-reference/MultiblockBuilder.md)
- [Callbacks & Events](api-reference/Callbacks-And-Events.md)

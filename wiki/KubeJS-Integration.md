[← Back to Home](index.md)

# KubeJS Integration

MultiLib exposes a KubeJS event group (`MultiblockEvents`) to create and modify definitions from `.js` scripts. Optional and reflection-gated: without the `kubejs` mod, none of it activates. If KubeJS is present but MultiLib was built without `kubejs_version` in `gradle.properties`, the `compat.kubejs` package is excluded and setup is skipped.

## When scripts run

Both `create` and `modify` live in **`server_scripts`**, not `startup_scripts`. Like JSON multiblocks, they re-fire on every `MultiblockDefinitionsReloadedEvent` — initial load *and* every `/reload` — so iterating doesn't need a restart.

Order per reload (driven by `KubeJSMultiblockSetup`):

1. `MultiblockEvents.create` — each listener declares a builder via `event.multiblock(id)`.
2. Every builder is built and swapped in via `MultiblockRegistry.replace(id, definition)` (remove-then-register), so a script safely re-declares its own multiblock each reload.
3. `MultiblockEvents.modify` — by now every Java/JSON/KubeJS definition is registered, so `event.modify(id, ...)` can patch any of them.

Because setup runs from `FMLCommonSetupEvent` and these events don't fire until the first reload after that, all block/item registries — including KubeJS's own `StartupEvents.registry(...)` — are already closed. A script's `key(symbol, block)` never resolves to a not-yet-registered block.

## Creating a multiblock

```js
MultiblockEvents.create(event => {
    event.multiblock('examplemod:my_altar')
        .layer('PPP', ' P ', ' G ')
        .layer('POP', ' P ', ' G ')
        .key('P', 'minecraft:stone_bricks')
        .key('O', 'examplemod:controller')
        .key('G', 'minecraft:gold_block')
        .core('O')
        .formationMode('automatic_and_wrench')
        .rotations('horizontal')
        .onFormed(ctx => MultiblockUtils.playSound(ctx, 'minecraft:block.beacon.activate'))
})
```

`event.multiblock(id)` returns a normal `MultiblockBuilder` — the whole [Java builder API](api-reference/MultiblockBuilder.md) is available from JS. **Don't call `.build()`** — the setup collects each returned builder and registers it for you, so `/reload` never throws "already registered". (The method is `multiblock(...)`, not `create(...)`, to avoid clashing with `StartupEvents.registry`'s own `event.create`.)

A validation failure is reported to the server log and the KubeJS console with `getLastValidationError()`. Two `create` listeners declaring the same id: the second replaces the first, with a warning.

### Registering a wrench

```js
MultiblockEvents.create(event => {
    event.wrench(Item.get('examplemod:my_wrench'))
})
```

The script-side equivalent of implementing `IMultiblockWrench` — for items made entirely in KubeJS, which can't implement a Java interface. See [Wrench tool](Advanced-Features.md#wrench-tool).

## Modifying a multiblock

`modify` patches **any** registered definition — Java, JSON, or KubeJS — by id:

```js
MultiblockEvents.modify(event => {
    event.modify('examplemod:my_altar', builder => {
        builder.key('G', 'minecraft:diamond_block')
        builder.onBroken(ctx => MultiblockUtils.summon(ctx, 'minecraft:lightning_bolt'))
    })
})
```

`event.modify(id, callback)` returns `true` on success, `false` (logged) when no definition exists under `id` or the rebuild fails validation. It snapshots via `MultiblockBuilder#toBuilder()`, applies your callback, rebuilds, and swaps in place — the original is untouched until the rebuild succeeds.

Everything flows through `toBuilder()`, so newer builder features are scriptable too: [`formedProperty(...)`](api-reference/MultiblockBuilder.md#formed-state-property), [tier stats](api-reference/MultiblockBuilder.md#tiers), and [variants](api-reference/MultiblockBuilder.md#variants) — e.g. `.variant('tall', v => { v.layer('III'); v.layer('ILI') })` (Rhino converts the arrow function to the `Consumer<VariantBuilder>` parameter).

## Wrench interaction events

```js
MultiblockEvents.wrench(event => {
    if (event.status === 'formation_failed') {
        event.player?.tell(`Couldn't form: ${event.failureReason}`)
    }
})
```

Fires on every wrench interaction, for any wrench (Java or `event.wrench(...)`-registered). MultiLib shows the player nothing on its own — scripts wanting feedback listen here.

`event.status` ∈ `"not_a_multiblock"`, `"already_formed"`, `"mode_disallows_wrench"`, `"formed"`, `"formation_failed"`, `"variant_changed"`. `event.multiblockId` is null only for `"not_a_multiblock"`; `event.failureReason` is set only for `"formation_failed"`; `event.fromVariant`/`event.toVariant` are set only for `"variant_changed"`.

## `MultiblockUtils` helpers

Script-friendly helpers bound globally as `MultiblockUtils`, translating Java concepts into string ids for use inside callbacks:

| Method | Behavior |
|---|---|
| `playSound(ctx, soundId[, volume, pitch])` | Plays a sound at the context position (unknown id: warns, no-ops). |
| `summon(ctx, entityId)` | Spawns an entity at the context position (unknown/disabled type: warns, no-ops). |

Both take the shared `MultiblockEventContext` that `onFormed`/`onBroken` receive.

## Limitations

- No script-side way to define a new `PatternProvider` type — the five built-ins are usable, but a custom shape needs a Java `PatternProviderSerializer`.
- `modify` replaces a definition wholesale via a rebuilt builder; there's no per-field read beyond the builder's own getters.

## See also

- [Advanced Features](Advanced-Features.md), [MultiblockBuilder](api-reference/MultiblockBuilder.md), [Callbacks & Events](api-reference/Callbacks-And-Events.md)

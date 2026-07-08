[← Back to Home](index.md)

# Getting Started

From "MultiLib is on my classpath" to a working structure that fires a callback when completed.

## Prerequisites

- A NeoForge 1.21.1 workspace (Java 21).
- Basic NeoForge modding — mod id, event bus, block/block-entity registration. New to NeoForge? Start with the [NeoForged docs](https://docs.neoforged.net/); this wiki assumes a working mod skeleton.

## 1. Add the dependency

MultiLib is a library mod: depend on it at compile and runtime, as with any NeoForge library. Add it to `build.gradle` and as a required dependency in `neoforge.mods.toml`, and ensure a MultiLib jar is present at runtime. Exact Maven/Modrinth/CurseForge coordinates depend on where it's published for your project.

## 2. The classes you'll touch

| Class | Role |
|---|---|
| `MultiLibAPI` | Entry point: `define(id)` starts a structure, `block(block)` declares block metadata. |
| `MultiblockBuilder` | Fluent builder: symbols, layers, rotation, formation mode, callbacks. |
| `MultiblockDefinition` | The immutable result of `.build()`. |
| `BlockIngredient` | What a symbol matches — a block, tag, block state, or predicate. |
| `MultiblockFormedCallback` / `MultiblockBrokenCallback` | Code that runs on form/break. |
| `AbstractMultiblockControllerBE` / `…Block` | Optional base classes for a controller block that tracks state. |

You mostly write `define(...)` chains, `BlockIngredient` values, and callback lambdas. The matchers and world tracker run behind the scenes after `.build()`.

## 3. Register a definition

Register once during mod setup, after the blocks you reference exist — typically from `FMLCommonSetupEvent`:

```java
public static void registerAll() {
    MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("examplemod", "my_altar"))
            .name("my_altar")
            .layer("PPP", " P ", " G ")
            .layer("POP", " P ", " G ")
            .key('P', BlockIngredient.of(Blocks.STONE_BRICKS))
            .key('O', BlockIngredient.of(MyBlocks.CONTROLLER_BLOCK))
            .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
            .core('O')
            .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
            .rotations(RotationMode.HORIZONTAL)
            .onFormed(ctx -> {
                // runs once the structure is detected
            })
            .build();
}
```

```java
@Mod(MyMod.MODID)
public class MyMod {
    public MyMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(MyPatterns::registerAll));
    }
}
```

`.build()` returns the definition **and** registers it. Use `.buildWithoutRegistering()` only when you need the object without registering (e.g. tests).

## 4. Runtime

Nothing else to call. MultiLib watches server-side block placement. When a block matching a definition's [activation symbol](Core-Concepts.md#core-and-activation-symbols) is placed (under `AUTOMATIC` or `AUTOMATIC_AND_WRENCH`), it tries to match the pattern around that position in every allowed orientation. On success it creates a persistent `MultiblockInstance` and runs the `onFormed` callbacks.

## 5. Test it

1. Launch a dev client with your mod loaded.
2. Build the pattern in-world. `layer(...)` rows read left-to-right as X, top-to-bottom as Z; **first `.layer(...)` is the top, last is the bottom** ([details](Core-Concepts.md#layers-and-the-coordinate-system)).
3. Place the core/activation block last — placing *it* triggers the check under `AUTOMATIC`.
4. The `onFormed` callback should fire.

Nothing happens? See [FAQ & Troubleshooting](FAQ-Troubleshooting.md).

## 6. Optional: a controller block

For a core that tracks formed state, exposes a menu, or ticks while formed, extend `AbstractMultiblockControllerBE`/`AbstractMultiblockControllerBlock` — see [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md). With `.model(...)` ([Master-Dummy model](Advanced-Features.md#master-dummy-model)), part blocks hide on formation; extend `AbstractMultiblockPartBlock` and use `.keepVisible(char...)` for symbols that should stay visible (e.g. IO ports).

## Next steps

- [Core Concepts](Core-Concepts.md) — symbols, layers, core/activation, formation modes.
- [API Reference](api-reference/MultiLibAPI.md) — full per-class reference.

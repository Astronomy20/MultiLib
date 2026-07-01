[← Back to Home](Home.md)

# Getting Started

This page gets you from "MultiLib is on my classpath" to "my first multiblock structure spawns a lightning bolt when completed."

## Prerequisites

- A NeoForge 1.21.1 mod workspace (Java 21).
- Familiarity with basic NeoForge modding (mod id, event bus, `@Mod` entry point, block/block-entity registration). If you're new to NeoForge itself, start with the [NeoForged documentation](https://docs.neoforged.net/) first — this wiki assumes you already have a working mod skeleton.

## 1. Add MultiLib as a dependency

MultiLib is a **library mod**: your mod depends on it at compile time and runtime, the same way you'd depend on any other NeoForge library mod. Add it to your `build.gradle` dependencies and to your `neoforge.mods.toml` as a required dependency, then make sure a MultiLib jar is present at runtime (dev environment and distribution).

> The exact Maven/Modrinth/CurseForge coordinates depend on where MultiLib is published for your project — check your project's distribution setup. This page assumes the dependency is already resolvable and `net.astronomy.multilib.*` classes are on your classpath.

## 2. Know the pieces you'll touch

| Class | Role |
|---|---|
| `MultiLibAPI` | Public entry point: `MultiLibAPI.define(id)` starts a new structure, `MultiLibAPI.block(block)` declares block-level metadata |
| `MultiblockBuilder` | Fluent builder for one structure: symbols, layers, rotation rules, formation mode, callbacks |
| `MultiblockDefinition` | The immutable, built structure — what you get back from `.build()` |
| `BlockIngredient` | What a pattern symbol matches against in the world (a single block, a tag, a block state, a predicate, ...) |
| `MultiblockFormedCallback` / `MultiblockBrokenCallback` | Functional interfaces: code that runs when the structure forms/breaks |
| `AbstractMultiblockControllerBE` / `AbstractMultiblockControllerBlock` | Optional base classes for a block-entity-backed "controller" block that tracks formed/unformed state |

You will mostly write `MultiLibAPI.define(...)` chains, `BlockIngredient` values, and callback lambdas. `MultiblockDefinition`, the internal matchers, and the world tracker work behind the scenes once you call `.build()`.

See [Core Concepts](Core-Concepts.md) for what each of these actually means structurally.

## 3. Register your first multiblock

Definitions must be registered once, during mod setup — **not** lazily inside event handlers, and not before the registries you reference (vanilla blocks, your mod's blocks) are available. The common pattern is a dedicated registration class called from `FMLCommonSetupEvent`:

```java
package net.examplemod.multiblock;

import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class MyPatterns {

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
                    Level level = ctx.level();
                    BlockPos origin = ctx.instance().getOrigin();
                    if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            lightning.moveTo(Vec3.atBottomCenterOf(origin));
                            serverLevel.addFreshEntity(lightning);
                        }
                    }
                })
                .build();
    }
}
```

```java
@Mod(MyMod.MODID)
public class MyMod {
    public MyMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MyPatterns::registerAll);
    }
}
```

`.build()` both returns the `MultiblockDefinition` **and** registers it into `MultiblockRegistry`. There is no "unregistered but built" mode here — unlike the old API, `.build()` always registers (use `.buildWithoutRegistering()` if you genuinely need a definition object without registering it, e.g. for testing).

## 4. What happens at runtime

You don't need to call anything else. MultiLib listens to block placement (`BlockEvent.EntityPlaceEvent`) on the server side. Whenever a block is placed that matches the **activation symbol** of a registered definition (by default, the core symbol — see [Core Concepts](Core-Concepts.md#core-and-activation-symbols)) with `FormationMode.AUTOMATIC` or `AUTOMATIC_AND_WRENCH`, MultiLib tries to match that definition's pattern around the placed position, in every orientation the definition allows. On a successful match, a `MultiblockInstance` is created, tracked persistently for that world, and every registered `onFormed` callback runs.

For the full mechanics (search order, rotation handling, what "matching" actually compares), see [Core Concepts](Core-Concepts.md) and the [Rotation & Matching Deep Dive](Rotation-And-Matching.md).

## 5. Sanity-check it

1. Launch a dev client/server with your mod loaded.
2. Place the blocks of your pattern in the world in the exact 2D layer layout you described (`layer(...)` rows are read top-to-bottom as Z, left-to-right as X for that Y level; **the first `.layer(...)` call is the top of the structure, the last call is the bottom** — see [Core Concepts](Core-Concepts.md#layers-and-the-coordinate-system)).
3. Place the **core/activation** block last — placement of *that* block is what triggers the match check under `FormationMode.AUTOMATIC`.
4. You should see the configured `onFormed` callback fire (in the example above: a lightning bolt at the structure's origin).

If nothing happens, check [FAQ & Troubleshooting](FAQ-Troubleshooting.md).

## 6. Optional: a stateful controller block

If your structure's core should track formed/unformed state, expose a menu, or run per-tick logic while formed, extend `AbstractMultiblockControllerBE`/`AbstractMultiblockControllerBlock` instead of a plain `Block`. See [Block Entity Abstractions](api-reference/BlockEntity-Abstractions.md) and the worked example in [Core Concepts](Core-Concepts.md#the-controller-block-entity-pattern).

If you also call `.model(...)` on the definition (see [Advanced Features § Master-Dummy model](Advanced-Features.md#master-dummy-model)), part blocks are automatically hidden once the structure forms — extend `AbstractMultiblockPartBlock` for any part block and use `.keepVisible(char...)` for symbols (like IO ports) that should stay visible.

## Next steps

- [Core Concepts](Core-Concepts.md) — understand symbols, layers, core/activation, and formation modes in depth before designing your own structures.
- [API Reference](Home.md#7-api-reference) — full method-by-method reference for every public class.

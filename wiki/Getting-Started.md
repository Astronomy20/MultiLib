# Getting Started

This page gets you from "MultiLib is on my classpath" to "my first multiblock structure spawns particles and plays a sound when completed."

## Prerequisites

- A NeoForge 1.21.1 mod workspace (Java 21).
- Familiarity with basic NeoForge modding (mod id, event bus, `@Mod` entry point). If you're new to NeoForge itself, start with the [NeoForged documentation](https://docs.neoforged.net/) first — this wiki assumes you already have a working mod skeleton.

## 1. Add MultiLib as a dependency

MultiLib is a **library mod**: your mod depends on it at compile time and runtime, the same way you'd depend on any other NeoForge library mod. Add it to your `build.gradle` dependencies and to your `neoforge.mods.toml` as a required dependency, then make sure a MultiLib jar is present at runtime (dev environment and distribution).

> The exact Maven/Modrinth/CurseForge coordinates depend on where MultiLib is published for your project — check your project's distribution setup. This page assumes the dependency is already resolvable and `net.astronomy.multilib.*` classes are on your classpath.

## 2. Know the four pieces you'll touch

| Class | Role |
|---|---|
| `PatternBuilder` | Fluent builder you use to describe a structure (keys, layers, rotation rules, action) |
| `PatternManager` | The immutable, built pattern — what you get back from `.build()` |
| `PatternAction` | A functional interface: the code that runs when the pattern is matched |
| `PatternRegistry` | Internal registry MultiLib uses to find candidate patterns when a block is placed |

You will mostly write `PatternBuilder` chains and `PatternAction` implementations. `PatternManager`, `PatternMatcher` and `PatternRegistry` work behind the scenes once you call `.build()`.

See [Core Concepts](Core-Concepts.md) for what each of these actually means structurally.

## 3. Register your first pattern

Patterns must be registered once, during mod setup — **not** lazily inside event handlers, and not before the registries you reference (vanilla blocks, your mod's blocks) are available. The common pattern is a dedicated registration class called from `FMLCommonSetupEvent`:

```java
package net.examplemod.pattern;

import net.astronomy.multilib.pattern.PatternAction;
import net.astronomy.multilib.pattern.PatternManager;
import net.minecraft.world.level.block.Blocks;

public class MyPatterns {

    public static void registerAll() {
        PatternManager.pattern()
                .key('D', Blocks.DIAMOND_BLOCK)
                .key('O', Blocks.OBSIDIAN)
                .key('E', Blocks.EMERALD_BLOCK)
                .layer(" D ",
                       "EDO",
                       " D ")
                .allowVerticalRotation(true)
                .allowSideRotation(false)
                .allowUpsideDown(false)
                .action((level, origin) -> {
                    PatternAction.playSound(level, origin);
                    PatternAction.spawnParticles(level, origin);
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
        event.enqueueWork(() -> {
            MyPatterns.registerAll();
        });
    }
}
```

`.build()` both returns the `PatternManager` **and** registers it (together with its action) into `PatternRegistry`, as long as you called `.action(...)` before `.build()`. If you never call `.action(...)`, the pattern is built but **not** auto-registered (see [PatternBuilder reference](api-reference/PatternBuilder.md)).

## 4. What happens at runtime

You don't need to call anything else. MultiLib listens to block placement (`BlockEvent.EntityPlaceEvent`) on the server side. Whenever a block is placed that matches one of the *key blocks* used in a registered pattern, MultiLib tries to match every registered pattern that uses that block around the placed position, in every position/rotation the pattern allows. The first pattern that matches has its `PatternAction.onMatch(...)` invoked once.

For the full mechanics (search order, rotation handling, what "matching" actually compares), see [Core Concepts](Core-Concepts.md) and the matching deep dive *(planned)*.

## 5. Sanity-check it

1. Launch a dev client/server with your mod loaded.
2. Place the blocks of your pattern in the world in the exact 2D layer layout you described (`layer(...)` rows are read top-to-bottom as Z, left-to-right as X for that Y level; layers are added bottom layer first — see [Core Concepts](Core-Concepts.md#layer-orientation)).
3. Place the **last** block of the structure last — placement of *that* block is what triggers the match check.
4. You should see the configured `PatternAction` fire (in the example above: a sound + particles at the pattern's origin).

If nothing happens, check [FAQ & Troubleshooting](FAQ-Troubleshooting.md) *(planned)*.

## Next steps

- [Core Concepts](Core-Concepts.md) — understand keys, layers, origin and registration in depth before designing your own patterns.
- [API Reference](Home.md#6-api-reference) — full method-by-method reference for every public class.

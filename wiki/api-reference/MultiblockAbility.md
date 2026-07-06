[← Back to Home](../index.md)

# `MultiblockAbility`

Package: `net.astronomy.multilib.api.ability`

A typed role a multiblock part can declare (e.g. "energy port", "item bus") - modeled after the "hatch"/"ability" concept from GregTech and Modern Industrialization. Unlike a fixed symbol-to-position mapping, any number of parts in a formed structure may declare the same ability; `MultiblockAbilities.get` collects all of them, so controller logic can ask "give me every energy port" instead of hardcoding a single symbol's position.

## `MultiblockAbility<T>`

```java
public final class MultiblockAbility<T> {
    public static <T> MultiblockAbility<T> create(ResourceLocation id, Class<T> type);
    public ResourceLocation id();
    public Class<T> type();
}
```

`T` is the concrete type (typically an interface the consuming mod's block entity implements) that `MultiblockAbilities.get` will cast matching parts to. Identity is the `id` alone - two abilities with the same id are equal regardless of `T`, so callers can look one up by id without needing the exact generic type in hand. Declare your abilities as `public static final MultiblockAbility<MyPortInterface>` fields, the same pattern as `StandardMultiblockState`.

## Declaring a part's abilities

```java
public interface IMultiblockPart {
    default Set<MultiblockAbility<?>> getAbilities() {
        return Set.of();
    }
    // ...
}
```

Override `getAbilities()` on your `AbstractMultiblockPartBE` (or controller) subclass to declare the role(s) it fulfills once part of a formed structure. Empty by default - most parts (plain structural blocks) provide no ability.

## `MultiblockAbilities`

```java
public final class MultiblockAbilities {
    public static <T> List<T> get(ServerLevel level, MultiblockInstance instance, MultiblockAbility<T> ability);
    public static <T> List<T> get(MultiblockContext ctx, MultiblockAbility<T> ability);
}
```

Resolves all parts of a formed multiblock instance that declare a given `MultiblockAbility`, cast to its type. Read-only - never places, breaks, or otherwise changes anything. Unloaded positions are silently skipped rather than forcing a chunk load. The `MultiblockContext` overload is a convenience that pulls the level and instance out of the context passed to `onFormed`/`onBroken`/tick callbacks.

```java
List<MyEnergyPort> ports = MultiblockAbilities.get(ctx, MyAbilities.ENERGY_PORT);
```

## See also

- [Block Entity Abstractions § IMultiblockPart](BlockEntity-Abstractions.md#imultiblockpart)
- [MultiblockComposition](MultiblockComposition.md) - a complementary way to ask "what block is at position Y" instead of "which parts fulfill role X".
- [Callbacks & Events](Callbacks-And-Events.md)

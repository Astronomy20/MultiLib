package net.astronomy.multilib.api.ability;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * A typed role a multiblock part can declare via {@link net.astronomy.multilib.api.blockentity.IMultiblockPart#getAbilities()}
 * (e.g. "energy port", "item bus") - modeled after the "hatch"/"ability" concept from GregTech and
 * Modern Industrialization. Unlike a fixed symbol-to-position mapping, any number of parts in a
 * formed structure may declare the same ability; {@link MultiblockAbilities#get} collects all of them.
 * <p>
 * {@code T} is the concrete type (typically an interface the consuming mod's block entity implements)
 * that {@link MultiblockAbilities#get} will cast matching parts to.
 */
public final class MultiblockAbility<T> {
    private final ResourceLocation id;
    private final Class<T> type;

    private MultiblockAbility(ResourceLocation id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public static <T> MultiblockAbility<T> create(ResourceLocation id, Class<T> type) {
        return new MultiblockAbility<>(id, type);
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    // Identity is the id alone - two abilities with the same id are the same ability regardless of
    // T, so callers can look one up by id without needing the exact generic type in hand.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiblockAbility<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "MultiblockAbility[" + id + "]";
    }
}

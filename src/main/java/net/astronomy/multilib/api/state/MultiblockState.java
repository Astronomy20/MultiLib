package net.astronomy.multilib.api.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class MultiblockState {
    private final ResourceLocation id;
    private final String nameTranslationKey;

    MultiblockState(ResourceLocation id, String nameTranslationKey) {  // package-private: solo MultiblockStateRegistry può istanziare
        this.id = id;
        this.nameTranslationKey = nameTranslationKey;
    }

    public ResourceLocation getId() { return id; }

    /** Set via {@link MultiblockStateRegistry#register(ResourceLocation, String)}; absent if the state was registered without a display name. */
    public Optional<String> getNameTranslationKey() { return Optional.ofNullable(nameTranslationKey); }

    @Override public String toString() { return id.toString(); }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiblockState other)) return false;
        return id.equals(other.id);
    }
    @Override public int hashCode() { return id.hashCode(); }
}

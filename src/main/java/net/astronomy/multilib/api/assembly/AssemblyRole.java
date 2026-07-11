package net.astronomy.multilib.api.assembly;

import net.minecraft.resources.ResourceLocation;

/**
 * A typed slot of an {@link AssemblyDefinition}: how many members of a given
 * {@link net.astronomy.multilib.api.definition.MultiblockDefinition} (referenced by id) the assembly
 * expects. {@code min == 0} makes the role optional.
 */
public record AssemblyRole(String name, ResourceLocation definition, int min, int max) {
    public AssemblyRole {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Assembly role name must be non-blank");
        }
        if (definition == null) {
            throw new IllegalArgumentException("Assembly role '" + name + "' has no definition");
        }
        if (min < 0) {
            throw new IllegalArgumentException("Assembly role '" + name + "' min must be >= 0, got " + min);
        }
        if (max < 1 || max < min) {
            throw new IllegalArgumentException(
                    "Assembly role '" + name + "' max must be >= 1 and >= min, got " + max + " (min " + min + ")");
        }
    }

    /** True when at least one member of this role is required for the assembly to form. */
    public boolean required() {
        return min >= 1;
    }
}

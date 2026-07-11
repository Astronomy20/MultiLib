package net.astronomy.multilib.api.assembly;

/**
 * How an assembly comes into being.
 */
public enum AssemblyFormationPolicy {
    /**
     * Default. Bottom-up: members form independently through the normal single-structure triggers,
     * then the {@code AssemblyMatcher} promotes a valid constellation into an assembly. Reuses the
     * existing formation path unchanged.
     */
    AGGREGATE,
    /**
     * Top-down: the assembly forms all members atomically from a single trigger. Not implemented in
     * v1 — reserved for a future release (see phase-12 non-goals).
     */
    ATOMIC
}

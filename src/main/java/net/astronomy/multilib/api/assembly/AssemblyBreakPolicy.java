package net.astronomy.multilib.api.assembly;

/**
 * What happens to an assembly when one of its member sub-structures breaks. Breaking the assembly
 * never destroys the members themselves — it only dissolves the logical link.
 */
public enum AssemblyBreakPolicy {
    /**
     * Default. If the lost member was optional and the assembly still satisfies every role's
     * minimum and every required connection, the assembly survives ({@code onMemberLeft}); otherwise
     * it breaks ({@code onAssemblyBroken}).
     */
    DEGRADE,
    /** Any member breaking breaks the whole assembly. */
    BREAK_ALL,
    /** The assembly never breaks on member loss: it drops to PARTIAL and recomposes when valid again. */
    PARTIAL_HOLD
}

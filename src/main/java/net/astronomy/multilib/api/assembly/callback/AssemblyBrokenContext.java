package net.astronomy.multilib.api.assembly.callback;

/** Passed to {@link AssemblyBrokenCallback} when an assembly dissolves. */
public record AssemblyBrokenContext(AssemblyContext context, Reason reason) {
    public enum Reason {
        /** A member sub-structure broke and the break policy dissolved the assembly. */
        MEMBER_LOST,
        /** Dissolved by an admin command or API call. */
        MANUAL
    }
}

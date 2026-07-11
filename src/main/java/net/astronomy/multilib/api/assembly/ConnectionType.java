package net.astronomy.multilib.api.assembly;

/**
 * How two assembly members are considered "connected" for the purposes of forming an
 * {@link AssemblyDefinition}. Each value reuses an existing MultiLib mechanism rather than inventing
 * a new one.
 */
public enum ConnectionType {
    /** The two members' block sets share at least one face (are orthogonally adjacent somewhere). */
    ADJACENCY,
    /** The two members have blocks within a Chebyshev distance of {@code radius} (see {@link ConnectionConstraint#radius()}). */
    PROXIMITY,
    /** The "from" member carries a port block (Fase 11) placed against a block of the "to" member. */
    PORT_LINK,
    /** The two members physically share a block (wall sharing, Fase 4). */
    SHARED_BLOCK
}

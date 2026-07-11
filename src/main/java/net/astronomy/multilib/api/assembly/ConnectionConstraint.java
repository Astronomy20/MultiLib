package net.astronomy.multilib.api.assembly;

/**
 * A single edge of an assembly's connection graph: the {@code toRole} member must be connected to a
 * {@code fromRole} member by {@code type}. {@code radius} is only meaningful for
 * {@link ConnectionType#PROXIMITY} (Chebyshev distance in blocks); it is ignored otherwise.
 *
 * <p>{@code required} constraints must be satisfied for the assembly to form. A non-required
 * constraint is advisory (it can pull an optional member in when present, but its absence does not
 * block formation).
 */
public record ConnectionConstraint(
        String fromRole,
        String toRole,
        ConnectionType type,
        int radius,
        boolean required
) {
    public ConnectionConstraint {
        if (fromRole == null || toRole == null || type == null) {
            throw new IllegalArgumentException("ConnectionConstraint requires non-null roles and type");
        }
        if (type == ConnectionType.PROXIMITY && radius <= 0) {
            throw new IllegalArgumentException("PROXIMITY connection requires a positive radius");
        }
    }

    public static ConnectionConstraint of(String fromRole, String toRole, ConnectionType type) {
        return new ConnectionConstraint(fromRole, toRole, type, 0, true);
    }

    public static ConnectionConstraint proximity(String fromRole, String toRole, int radius) {
        return new ConnectionConstraint(fromRole, toRole, ConnectionType.PROXIMITY, radius, true);
    }
}

package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.assembly.AssemblyRole;

/**
 * Role-count validity of an assembly: every role's current member count is within {@code [min, max]}.
 * v1 uses this as the proxy for "the assembly is still valid" on member loss/gain; full re-validation
 * of the connection graph is a documented follow-up.
 */
public final class AssemblyValidity {

    private AssemblyValidity() {}

    public static boolean isValid(AssemblyDefinition def, AssemblyInstance assembly) {
        for (AssemblyRole role : def.getRoles().values()) {
            int count = assembly.memberCount(role.name());
            if (count < role.min() || count > role.max()) return false;
        }
        return true;
    }
}

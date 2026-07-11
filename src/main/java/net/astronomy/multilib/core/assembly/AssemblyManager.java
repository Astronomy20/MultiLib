package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.assembly.StandardAssemblyState;
import net.astronomy.multilib.api.assembly.callback.AssemblyBrokenContext;
import net.astronomy.multilib.api.assembly.callback.AssemblyContext;
import net.astronomy.multilib.api.assembly.callback.AssemblyFormedContext;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberContext;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central lifecycle operations for assemblies (form, grow, dissolve) plus fault-tolerant callback
 * dispatch. Shared by {@link AssemblyMatcher} (formation/growth) and {@code AssemblyBreakHandler}
 * (break). All methods run on the server thread.
 */
public final class AssemblyManager {

    private AssemblyManager() {}

    /** Creates and registers a new assembly from a validated set of members, firing formed + joined callbacks. */
    public static AssemblyInstance formAssembly(ServerLevel level, AssemblyDefinition def,
                                                Map<String, Set<UUID>> membersByRole, Optional<UUID> formedBy) {
        WorldAssemblyTracker tracker = WorldAssemblyTracker.get(level);
        AssemblyInstance assembly = new AssemblyInstance(UUID.randomUUID(), def.getId(), formedBy);
        for (Map.Entry<String, Set<UUID>> e : membersByRole.entrySet()) {
            for (UUID memberId : e.getValue()) assembly.addMember(e.getKey(), memberId);
        }
        assembly.setState(StandardAssemblyState.FORMED);
        tracker.register(assembly);

        AssemblyContext ctx = new AssemblyContext(level, assembly, def);
        fireFormed(def, new AssemblyFormedContext(ctx));
        for (Map.Entry<String, Set<UUID>> e : membersByRole.entrySet()) {
            for (UUID memberId : e.getValue()) {
                fireMemberJoined(def, new AssemblyMemberContext(ctx, e.getKey(), memberId));
            }
        }
        return assembly;
    }

    /** Adds one member to an already-formed assembly (incremental growth), firing the joined callback. */
    public static void addMember(ServerLevel level, AssemblyDefinition def, AssemblyInstance assembly,
                                 String role, UUID memberId) {
        if (!assembly.addMember(role, memberId)) return;
        WorldAssemblyTracker tracker = WorldAssemblyTracker.get(level);
        tracker.indexMember(memberId, assembly.getId());
        // A PARTIAL_HOLD assembly that was waiting on this role flips back to FORMED once valid again.
        if (AssemblyValidity.isValid(def, assembly)) assembly.setState(StandardAssemblyState.FORMED);
        AssemblyContext ctx = new AssemblyContext(level, assembly, def);
        fireMemberJoined(def, new AssemblyMemberContext(ctx, role, memberId));
    }

    /** Dissolves an assembly entirely (members are untouched), firing the broken callback. */
    public static void dissolve(ServerLevel level, AssemblyDefinition def, AssemblyInstance assembly,
                                AssemblyBrokenContext.Reason reason) {
        WorldAssemblyTracker tracker = WorldAssemblyTracker.get(level);
        tracker.unregister(assembly.getId());
        AssemblyContext ctx = new AssemblyContext(level, assembly, def);
        fireBroken(def, new AssemblyBrokenContext(ctx, reason));
    }

    public static void fireFormed(AssemblyDefinition def, AssemblyFormedContext ctx) {
        def.getFormedCallbacks().forEach(cb -> guard(def, "onAssemblyFormed", () -> cb.onFormed(ctx)));
    }

    public static void fireBroken(AssemblyDefinition def, AssemblyBrokenContext ctx) {
        def.getBrokenCallbacks().forEach(cb -> guard(def, "onAssemblyBroken", () -> cb.onBroken(ctx)));
    }

    public static void fireMemberJoined(AssemblyDefinition def, AssemblyMemberContext ctx) {
        def.getMemberJoinedCallbacks().forEach(cb -> guard(def, "onMemberJoined", () -> cb.onMemberJoined(ctx)));
    }

    public static void fireMemberLeft(AssemblyDefinition def, AssemblyMemberContext ctx) {
        def.getMemberLeftCallbacks().forEach(cb -> guard(def, "onMemberLeft", () -> cb.onMemberLeft(ctx)));
    }

    private static void guard(AssemblyDefinition def, String which, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            MultiLib.LOGGER.error("[MultiLib] {} callback for assembly '{}' threw", which, def.getId(), e);
        }
    }
}

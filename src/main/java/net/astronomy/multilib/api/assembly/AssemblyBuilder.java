package net.astronomy.multilib.api.assembly;

import net.astronomy.multilib.api.assembly.callback.AssemblyBrokenCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyFormedCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberJoinedCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberLeftCallback;
import net.astronomy.multilib.core.assembly.AssemblyRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fluent builder for {@link AssemblyDefinition}. Validation is fail-fast in {@link #build()} — an
 * incoherent assembly throws at load time, not silently at runtime. {@link #build()} also registers
 * the definition into {@link AssemblyRegistry}, mirroring {@code MultiblockBuilder}.
 */
public final class AssemblyBuilder {
    private ResourceLocation id;
    private final Map<String, AssemblyRole> roles = new LinkedHashMap<>();
    private final List<ConnectionConstraint> connections = new ArrayList<>();
    private String masterRole = null;
    private AssemblyBreakPolicy breakPolicy = AssemblyBreakPolicy.DEGRADE;
    private AssemblyFormationPolicy formationPolicy = AssemblyFormationPolicy.AGGREGATE;
    private int priority = 0;
    private final Map<String, StatMerge> aggregateStats = new LinkedHashMap<>();
    private final List<AssemblyFormedCallback> formedCallbacks = new ArrayList<>();
    private final List<AssemblyBrokenCallback> brokenCallbacks = new ArrayList<>();
    private final List<AssemblyMemberJoinedCallback> memberJoinedCallbacks = new ArrayList<>();
    private final List<AssemblyMemberLeftCallback> memberLeftCallbacks = new ArrayList<>();

    private AssemblyBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static AssemblyBuilder create(ResourceLocation id) {
        return new AssemblyBuilder(id);
    }

    /** Reconstructs a builder from an existing definition (lossless), for KubeJS {@code modify}. */
    public static AssemblyBuilder from(AssemblyDefinition def) {
        AssemblyBuilder b = new AssemblyBuilder(def.getId());
        b.roles.putAll(def.getRoles());
        b.connections.addAll(def.getConnections());
        b.masterRole = def.getMasterRole().orElse(null);
        b.breakPolicy = def.getBreakPolicy();
        b.formationPolicy = def.getFormationPolicy();
        b.priority = def.getPriority();
        b.aggregateStats.putAll(def.getAggregateStats());
        b.formedCallbacks.addAll(def.getFormedCallbacks());
        b.brokenCallbacks.addAll(def.getBrokenCallbacks());
        b.memberJoinedCallbacks.addAll(def.getMemberJoinedCallbacks());
        b.memberLeftCallbacks.addAll(def.getMemberLeftCallbacks());
        return b;
    }

    public AssemblyBuilder id(ResourceLocation id) { this.id = id; return this; }

    /** Required singleton role (min 1, max 1). */
    public AssemblyBuilder role(String name, ResourceLocation definition) {
        return role(name, definition, 1, 1);
    }

    public AssemblyBuilder role(String name, ResourceLocation definition, int min, int max) {
        roles.put(name, new AssemblyRole(name, definition, min, max));
        return this;
    }

    public AssemblyBuilder role(AssemblyRole role) {
        roles.put(role.name(), role);
        return this;
    }

    public AssemblyBuilder connection(String fromRole, String toRole, ConnectionType type) {
        connections.add(ConnectionConstraint.of(fromRole, toRole, type));
        return this;
    }

    public AssemblyBuilder connection(ConnectionConstraint constraint) {
        connections.add(constraint);
        return this;
    }

    public AssemblyBuilder proximity(String fromRole, String toRole, int radius) {
        connections.add(ConnectionConstraint.proximity(fromRole, toRole, radius));
        return this;
    }

    public AssemblyBuilder masterRole(String role) { this.masterRole = role; return this; }
    public AssemblyBuilder breakPolicy(AssemblyBreakPolicy policy) { this.breakPolicy = policy; return this; }
    public AssemblyBuilder formationPolicy(AssemblyFormationPolicy policy) { this.formationPolicy = policy; return this; }
    public AssemblyBuilder priority(int priority) { this.priority = priority; return this; }
    public AssemblyBuilder aggregateStat(String key, StatMerge merge) { aggregateStats.put(key, merge); return this; }

    public AssemblyBuilder onAssemblyFormed(AssemblyFormedCallback cb) { formedCallbacks.add(cb); return this; }
    public AssemblyBuilder onAssemblyBroken(AssemblyBrokenCallback cb) { brokenCallbacks.add(cb); return this; }
    public AssemblyBuilder onMemberJoined(AssemblyMemberJoinedCallback cb) { memberJoinedCallbacks.add(cb); return this; }
    public AssemblyBuilder onMemberLeft(AssemblyMemberLeftCallback cb) { memberLeftCallbacks.add(cb); return this; }

    /** Validates, constructs, registers, and returns the definition. Throws on any inconsistency. */
    public AssemblyDefinition build() {
        AssemblyDefinition def = buildWithoutRegister();
        AssemblyRegistry.register(def);
        return def;
    }

    /** Same validation/construction as {@link #build()} but without registering (used by loaders that register separately). */
    public AssemblyDefinition buildWithoutRegister() {
        if (id == null) {
            throw new IllegalStateException("AssemblyDefinition must have an id");
        }
        if (roles.isEmpty()) {
            throw new IllegalStateException("AssemblyDefinition '" + id + "' must declare at least one role");
        }
        boolean anyRequired = roles.values().stream().anyMatch(AssemblyRole::required);
        if (!anyRequired) {
            throw new IllegalStateException(
                    "AssemblyDefinition '" + id + "' has no required role (every role has min 0) — it could never meaningfully form");
        }
        if (masterRole != null && !roles.containsKey(masterRole)) {
            throw new IllegalStateException(
                    "AssemblyDefinition '" + id + "' masterRole '" + masterRole + "' is not a declared role");
        }
        for (ConnectionConstraint c : connections) {
            if (!roles.containsKey(c.fromRole())) {
                throw new IllegalStateException(
                        "AssemblyDefinition '" + id + "' connection references unknown fromRole '" + c.fromRole() + "'");
            }
            if (!roles.containsKey(c.toRole())) {
                throw new IllegalStateException(
                        "AssemblyDefinition '" + id + "' connection references unknown toRole '" + c.toRole() + "'");
            }
        }
        if (formationPolicy == AssemblyFormationPolicy.ATOMIC) {
            throw new IllegalStateException(
                    "AssemblyDefinition '" + id + "' uses FormationPolicy.ATOMIC, which is not implemented in v1 (use AGGREGATE)");
        }
        return new AssemblyDefinition(id, roles, connections,
                Optional.ofNullable(masterRole), breakPolicy, formationPolicy, priority, aggregateStats,
                formedCallbacks, brokenCallbacks, memberJoinedCallbacks, memberLeftCallbacks);
    }
}

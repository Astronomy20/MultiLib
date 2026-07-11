package net.astronomy.multilib.api.assembly;

import net.astronomy.multilib.api.assembly.callback.AssemblyBrokenCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyFormedCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberJoinedCallback;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberLeftCallback;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable description of an assembly: which member roles it has, how they must be connected, and
 * how it behaves on formation/break. Built through {@link AssemblyBuilder}; frozen after
 * {@code build()}. Only {@link AssemblyInstance} is mutable.
 */
public final class AssemblyDefinition {
    private final ResourceLocation id;
    private final Map<String, AssemblyRole> roles;
    private final List<ConnectionConstraint> connections;
    private final Optional<String> masterRole;
    private final AssemblyBreakPolicy breakPolicy;
    private final AssemblyFormationPolicy formationPolicy;
    private final int priority;
    private final Map<String, StatMerge> aggregateStats;
    private final List<AssemblyFormedCallback> formedCallbacks;
    private final List<AssemblyBrokenCallback> brokenCallbacks;
    private final List<AssemblyMemberJoinedCallback> memberJoinedCallbacks;
    private final List<AssemblyMemberLeftCallback> memberLeftCallbacks;

    AssemblyDefinition(ResourceLocation id,
                       Map<String, AssemblyRole> roles,
                       List<ConnectionConstraint> connections,
                       Optional<String> masterRole,
                       AssemblyBreakPolicy breakPolicy,
                       AssemblyFormationPolicy formationPolicy,
                       int priority,
                       Map<String, StatMerge> aggregateStats,
                       List<AssemblyFormedCallback> formedCallbacks,
                       List<AssemblyBrokenCallback> brokenCallbacks,
                       List<AssemblyMemberJoinedCallback> memberJoinedCallbacks,
                       List<AssemblyMemberLeftCallback> memberLeftCallbacks) {
        this.id = id;
        this.roles = Collections.unmodifiableMap(roles);
        this.connections = Collections.unmodifiableList(connections);
        this.masterRole = masterRole;
        this.breakPolicy = breakPolicy;
        this.formationPolicy = formationPolicy;
        this.priority = priority;
        this.aggregateStats = Collections.unmodifiableMap(aggregateStats);
        this.formedCallbacks = Collections.unmodifiableList(formedCallbacks);
        this.brokenCallbacks = Collections.unmodifiableList(brokenCallbacks);
        this.memberJoinedCallbacks = Collections.unmodifiableList(memberJoinedCallbacks);
        this.memberLeftCallbacks = Collections.unmodifiableList(memberLeftCallbacks);
    }

    public ResourceLocation getId() { return id; }
    public Map<String, AssemblyRole> getRoles() { return roles; }
    public Optional<AssemblyRole> getRole(String name) { return Optional.ofNullable(roles.get(name)); }
    public List<ConnectionConstraint> getConnections() { return connections; }
    public Optional<String> getMasterRole() { return masterRole; }
    public AssemblyBreakPolicy getBreakPolicy() { return breakPolicy; }
    public AssemblyFormationPolicy getFormationPolicy() { return formationPolicy; }
    public int getPriority() { return priority; }
    public Map<String, StatMerge> getAggregateStats() { return aggregateStats; }
    public List<AssemblyFormedCallback> getFormedCallbacks() { return formedCallbacks; }
    public List<AssemblyBrokenCallback> getBrokenCallbacks() { return brokenCallbacks; }
    public List<AssemblyMemberJoinedCallback> getMemberJoinedCallbacks() { return memberJoinedCallbacks; }
    public List<AssemblyMemberLeftCallback> getMemberLeftCallbacks() { return memberLeftCallbacks; }

    /** The definition ids this assembly can ever reference, across all roles. */
    public java.util.Set<ResourceLocation> referencedDefinitions() {
        java.util.Set<ResourceLocation> set = new java.util.HashSet<>();
        for (AssemblyRole role : roles.values()) set.add(role.definition());
        return set;
    }

    /** Which role a given member definition id can fill (first match), if any. */
    public Optional<String> roleForDefinition(ResourceLocation definitionId) {
        for (AssemblyRole role : roles.values()) {
            if (role.definition().equals(definitionId)) return Optional.of(role.name());
        }
        return Optional.empty();
    }

    public AssemblyBuilder toBuilder() {
        return AssemblyBuilder.from(this);
    }
}

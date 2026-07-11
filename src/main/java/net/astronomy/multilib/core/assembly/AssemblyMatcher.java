package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.assembly.AssemblyRole;
import net.astronomy.multilib.api.assembly.ConnectionConstraint;
import net.astronomy.multilib.api.event.MultiblockFormedEvent;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bottom-up (AGGREGATE) assembly formation. Subscribes to {@link MultiblockFormedEvent} on the game
 * bus — so it hooks the existing single-structure formation path without modifying it — and, when a
 * member forms, tries to either grow an existing assembly or promote a newly-complete constellation
 * into a fresh one. Registered automatically via {@link EventBusSubscriber}; no edits to MultiLib.java.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class AssemblyMatcher {

    private AssemblyMatcher() {}

    @SubscribeEvent
    public static void onMultiblockFormed(MultiblockFormedEvent event) {
        ServerLevel level = event.getLevel();
        MultiblockInstance formed = event.getInstance();
        ResourceLocation memberDefId = formed.getDefinitionId();

        List<AssemblyDefinition> candidates = AssemblyRegistry.candidatesForMember(memberDefId);
        if (candidates.isEmpty()) return;

        WorldAssemblyTracker aTracker = WorldAssemblyTracker.get(level);
        if (aTracker.isMemberClaimed(formed.getId())) return; // already part of an assembly

        WorldMultiblockTracker mTracker = WorldMultiblockTracker.get(level);

        for (AssemblyDefinition def : candidates) {
            Optional<String> roleOpt = def.roleForDefinition(memberDefId);
            if (roleOpt.isEmpty()) continue;
            String role = roleOpt.get();

            // 1) Incremental growth: attach to an existing assembly of this def that has room.
            if (tryGrowExisting(level, def, formed, role, mTracker, aTracker)) return;

            // 2) Fresh formation from this seed.
            Optional<Map<String, Set<UUID>>> members = tryForm(level, def, formed, mTracker, aTracker);
            if (members.isPresent()) {
                AssemblyManager.formAssembly(level, def, members.get(), formed.getFormedBy());
                return;
            }
        }
    }

    private static boolean tryGrowExisting(ServerLevel level, AssemblyDefinition def, MultiblockInstance formed,
                                           String role, WorldMultiblockTracker mTracker, WorldAssemblyTracker aTracker) {
        AssemblyRole roleSpec = def.getRole(role).orElseThrow();
        for (AssemblyInstance a : aTracker.getAll()) {
            if (!a.getDefinitionId().equals(def.getId())) continue;
            if (a.memberCount(role) >= roleSpec.max()) continue;
            // Connected to any current member of this assembly?
            for (UUID memberId : a.allMemberIds()) {
                MultiblockInstance existing = mTracker.getById(memberId).orElse(null);
                if (existing == null) continue;
                if (connected(level, def, existing, formed)) {
                    AssemblyManager.addMember(level, def, a, role, formed.getId());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to gather a valid member set for {@code def}, BFS-connected to {@code seed} through the
     * connection graph, using only unclaimed formed members. Returns the members grouped by role, or
     * empty if no valid constellation exists yet.
     */
    private static Optional<Map<String, Set<UUID>>> tryForm(ServerLevel level, AssemblyDefinition def,
                                                            MultiblockInstance seed, WorldMultiblockTracker mTracker,
                                                            WorldAssemblyTracker aTracker) {
        Set<ResourceLocation> memberDefs = def.referencedDefinitions();

        // Candidate pool: every unclaimed formed member whose definition this assembly references.
        List<MultiblockInstance> pool = new ArrayList<>();
        for (MultiblockInstance mi : mTracker.getAllInstances()) {
            if (!memberDefs.contains(mi.getDefinitionId())) continue;
            if (aTracker.isMemberClaimed(mi.getId())) continue;
            pool.add(mi);
        }

        // BFS from the seed over connection edges.
        Map<UUID, MultiblockInstance> included = new HashMap<>();
        included.put(seed.getId(), seed);
        Deque<MultiblockInstance> frontier = new ArrayDeque<>();
        frontier.add(seed);
        while (!frontier.isEmpty()) {
            MultiblockInstance current = frontier.poll();
            for (MultiblockInstance cand : pool) {
                if (included.containsKey(cand.getId())) continue;
                if (connected(level, def, current, cand)) {
                    included.put(cand.getId(), cand);
                    frontier.add(cand);
                }
            }
        }

        // Group by role and validate multiplicities.
        Map<String, Set<UUID>> byRole = new HashMap<>();
        for (MultiblockInstance mi : included.values()) {
            Optional<String> r = def.roleForDefinition(mi.getDefinitionId());
            if (r.isEmpty()) continue;
            byRole.computeIfAbsent(r.get(), k -> new HashSet<>()).add(mi.getId());
        }
        for (AssemblyRole roleSpec : def.getRoles().values()) {
            int count = byRole.getOrDefault(roleSpec.name(), Set.of()).size();
            if (count < roleSpec.min()) return Optional.empty();   // not enough yet — wait
            if (count > roleSpec.max()) return Optional.empty();   // ambiguous over-cap — v1 declines
        }
        return Optional.of(byRole);
    }

    /**
     * True if members {@code a} and {@code b} satisfy at least one declared connection constraint (in
     * either direction) between their roles. If the assembly declares no constraints at all, falls
     * back to plain adjacency so a connection-free assembly still requires one contiguous cluster.
     */
    private static boolean connected(ServerLevel level, AssemblyDefinition def,
                                     MultiblockInstance a, MultiblockInstance b) {
        Optional<String> ra = def.roleForDefinition(a.getDefinitionId());
        Optional<String> rb = def.roleForDefinition(b.getDefinitionId());
        if (ra.isEmpty() || rb.isEmpty()) return false;

        if (def.getConnections().isEmpty()) {
            return ConnectionEvaluator.connected(level, a, b,
                    net.astronomy.multilib.api.assembly.ConnectionType.ADJACENCY, 0);
        }
        for (ConnectionConstraint c : def.getConnections()) {
            boolean forward = c.fromRole().equals(ra.get()) && c.toRole().equals(rb.get());
            boolean backward = c.fromRole().equals(rb.get()) && c.toRole().equals(ra.get());
            if (!forward && !backward) continue;
            if (ConnectionEvaluator.connected(level, a, b, c.type(), c.radius())) return true;
        }
        return false;
    }
}

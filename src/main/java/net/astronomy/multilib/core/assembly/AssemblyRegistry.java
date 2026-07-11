package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static registry of {@link AssemblyDefinition}s, mirroring {@code MultiblockRegistry}. Also keeps a
 * reverse index from a member definition id to the assemblies that reference it, so the matcher only
 * ever considers assemblies that a freshly-formed member could actually belong to.
 */
public final class AssemblyRegistry {
    private static final Map<ResourceLocation, AssemblyDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, List<AssemblyDefinition>> BY_MEMBER_DEFINITION = new HashMap<>();
    private static final Set<ResourceLocation> JSON_IDS = new HashSet<>();

    private AssemblyRegistry() {}

    public static void register(AssemblyDefinition definition) {
        ResourceLocation id = definition.getId();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("AssemblyDefinition already registered: " + id);
        }
        addInternal(definition);
    }

    public static void registerJson(AssemblyDefinition definition) {
        JSON_IDS.add(definition.getId());
        // JSON (re)load may re-register; replace instead of throwing.
        removeInternal(definition.getId());
        addInternal(definition);
    }

    /** Replaces {@code oldId} with {@code replacement} (KubeJS/Java modify), tolerating a rename. */
    public static void replace(ResourceLocation oldId, AssemblyDefinition replacement) {
        removeInternal(oldId);
        removeInternal(replacement.getId());
        addInternal(replacement);
    }

    public static void clearJsonDefinitions() {
        for (ResourceLocation id : new HashSet<>(JSON_IDS)) removeInternal(id);
        JSON_IDS.clear();
    }

    private static void addInternal(AssemblyDefinition definition) {
        DEFINITIONS.put(definition.getId(), definition);
        for (ResourceLocation memberDef : definition.referencedDefinitions()) {
            BY_MEMBER_DEFINITION.computeIfAbsent(memberDef, k -> new ArrayList<>()).add(definition);
        }
    }

    private static void removeInternal(ResourceLocation id) {
        AssemblyDefinition def = DEFINITIONS.remove(id);
        if (def == null) return;
        for (ResourceLocation memberDef : def.referencedDefinitions()) {
            List<AssemblyDefinition> list = BY_MEMBER_DEFINITION.get(memberDef);
            if (list != null) {
                list.removeIf(d -> d.getId().equals(id));
                if (list.isEmpty()) BY_MEMBER_DEFINITION.remove(memberDef);
            }
        }
    }

    public static Optional<AssemblyDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Collection<AssemblyDefinition> getAll() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    /**
     * Assemblies that could contain a member of {@code memberDefinition}, highest priority first
     * (tie-break: JSON before Java, then lexicographic id) — so the matcher tries the most specific
     * candidate first and a member ends up in at most one assembly.
     */
    public static List<AssemblyDefinition> candidatesForMember(ResourceLocation memberDefinition) {
        List<AssemblyDefinition> list = new ArrayList<>(BY_MEMBER_DEFINITION.getOrDefault(memberDefinition, List.of()));
        list.sort(Comparator.comparingInt(AssemblyDefinition::getPriority).reversed()
                .thenComparing(def -> JSON_IDS.contains(def.getId()) ? 0 : 1)
                .thenComparing(def -> def.getId().toString()));
        return list;
    }
}

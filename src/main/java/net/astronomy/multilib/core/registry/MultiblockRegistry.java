package net.astronomy.multilib.core.registry;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

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

public final class MultiblockRegistry {
    private static final Map<ResourceLocation, MultiblockDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Block, List<MultiblockDefinition>> BLOCK_INDEX = new HashMap<>();
    private static final List<MultiblockDefinition> ALWAYS_CHECKED = new ArrayList<>();
    private static final Set<ResourceLocation> JSON_DEFINITION_IDS = new HashSet<>();

    private MultiblockRegistry() {}

    public static void register(MultiblockDefinition definition) {
        ResourceLocation id = definition.getId();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("MultiblockDefinition already registered: " + id);
        }
        DEFINITIONS.put(id, definition);
        Set<Block> candidates = definition.getCandidateBlocks();
        if (candidates.isEmpty()) {
            ALWAYS_CHECKED.add(definition);
        } else {
            for (Block block : candidates) {
                BLOCK_INDEX.computeIfAbsent(block, k -> new ArrayList<>()).add(definition);
            }
        }
    }

    public static void registerJson(MultiblockDefinition definition) {
        JSON_DEFINITION_IDS.add(definition.getId());
        register(definition);
    }

    public static void clearJsonDefinitions() {
        for (ResourceLocation id : new HashSet<>(JSON_DEFINITION_IDS)) {
            MultiblockDefinition def = DEFINITIONS.remove(id);
            if (def == null) continue;
            Set<Block> candidates = def.getCandidateBlocks();
            if (candidates.isEmpty()) {
                ALWAYS_CHECKED.removeIf(d -> d.getId().equals(id));
            } else {
                for (Block block : candidates) {
                    List<MultiblockDefinition> list = BLOCK_INDEX.get(block);
                    if (list != null) {
                        list.removeIf(d -> d.getId().equals(id));
                        if (list.isEmpty()) BLOCK_INDEX.remove(block);
                    }
                }
            }
        }
        JSON_DEFINITION_IDS.clear();
    }

    public static Optional<MultiblockDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static Collection<MultiblockDefinition> getAll() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    /** Alias for {@link #getAll()}; exposed for soft-dependency compat integrations. */
    public static Collection<MultiblockDefinition> getAllDefinitions() {
        return getAll();
    }

    public static List<MultiblockDefinition> getCandidatesFor(Block block) {
        List<MultiblockDefinition> result = new ArrayList<>();
        result.addAll(BLOCK_INDEX.getOrDefault(block, List.of()));
        result.addAll(ALWAYS_CHECKED);
        // Explicit priority is the author's call and always wins. Only when two definitions tie
        // (most commonly: both left at the default 0) does a datapack-defined pattern get tried
        // before a code-defined one — same "data overrides hardcoded defaults" convention vanilla
        // uses for recipes/loot tables/tags, but kept separate from `priority` itself so setting an
        // explicit priority on a Java pattern can still rank it above any datapack pattern.
        result.sort(Comparator.comparingInt(MultiblockDefinition::getPriority).reversed()
                .thenComparing(def -> JSON_DEFINITION_IDS.contains(def.getId()) ? 0 : 1));
        return result;
    }
}

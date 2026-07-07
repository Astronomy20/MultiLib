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
    private static final Map<ResourceLocation, Source> SOURCES = new HashMap<>();

    private MultiblockRegistry() {}

    /**
     * Which mechanism actually registered a given definition - used purely for dev-facing display
     * (e.g. the JEI/REI/EMI preview panel's dev-mode "where is this saved" label), never for matching/
     * priority logic (that's still {@link #JSON_DEFINITION_IDS} + {@link MultiblockDefinition#getPriority()},
     * unaffected by this).
     */
    public enum Source { JAVA, JSON, KUBEJS }

    /** Defaults to {@link Source#JAVA} - the only other direct caller of plain {@code register} is {@link #registerJson}, which always passes {@link Source#JSON} explicitly. */
    public static void register(MultiblockDefinition definition) {
        register(definition, Source.JAVA);
    }

    public static void register(MultiblockDefinition definition, Source source) {
        ResourceLocation id = definition.getId();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("MultiblockDefinition already registered: " + id);
        }
        DEFINITIONS.put(id, definition);
        SOURCES.put(id, source);
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
        register(definition, Source.JSON);
    }

    /**
     * Removes whatever definition is currently registered under {@code oldId} (Java, JSON, or
     * previously KubeJS-registered) and registers {@code replacement} in its place, keeping whichever
     * {@link Source} {@code oldId} was already tagged with (falling back to {@link Source#JAVA} if it
     * had none) - this overload is for callers patching an *existing* definition in place
     * ({@code MultiLibAPI#redefine}, {@code MultiblockEvents.modify}), which doesn't change where the
     * definition originally came from. {@code oldId} and {@code replacement.getId()} are taken
     * separately (rather than always replacing under the same id) so a caller that renamed the
     * definition via {@code builder.id(...)} still cleans up the original id instead of leaving a stale
     * duplicate behind. Unlike {@link #register}, does not throw if {@code replacement.getId()} already
     * exists under a different entry.
     */
    public static void replace(ResourceLocation oldId, MultiblockDefinition replacement) {
        replace(oldId, replacement, SOURCES.getOrDefault(oldId, Source.JAVA));
    }

    /** Same as {@link #replace(ResourceLocation, MultiblockDefinition)}, but tags {@code replacement} with an explicit {@link Source} instead of inheriting {@code oldId}'s - for callers that know the source doesn't change (e.g. {@code MultiblockEvents.create}, always {@link Source#KUBEJS}). */
    public static void replace(ResourceLocation oldId, MultiblockDefinition replacement, Source source) {
        removeInternal(oldId);
        removeInternal(replacement.getId());
        register(replacement, source);
    }

    public static void clearJsonDefinitions() {
        for (ResourceLocation id : new HashSet<>(JSON_DEFINITION_IDS)) {
            removeInternal(id);
        }
        JSON_DEFINITION_IDS.clear();
    }

    /** Which mechanism registered {@code id}, or empty if nothing is registered under it. */
    public static Optional<Source> getSource(ResourceLocation id) {
        return Optional.ofNullable(SOURCES.get(id));
    }

    private static void removeInternal(ResourceLocation id) {
        MultiblockDefinition def = DEFINITIONS.remove(id);
        SOURCES.remove(id);
        if (def == null) return;
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
        // before a code-defined one - same "data overrides hardcoded defaults" convention vanilla
        // uses for recipes/loot tables/tags, but kept separate from `priority` itself so setting an
        // explicit priority on a Java pattern can still rank it above any datapack pattern.
        result.sort(Comparator.comparingInt(MultiblockDefinition::getPriority).reversed()
                .thenComparing(def -> JSON_DEFINITION_IDS.contains(def.getId()) ? 0 : 1));
        return result;
    }
}

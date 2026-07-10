package net.astronomy.multilib.core.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-position override of which {@link net.astronomy.multilib.api.definition.MultiblockDefinition} a
 * block should resolve to, for the (uncommon but real) case where the same block type is a valid core
 * or activation symbol for more than one registered definition - a plain block-type-keyed priority
 * order (see {@link MultiblockRegistry#getCandidatesFor}) can't disambiguate that, since it has no
 * notion of *which* structure a player is actually building at a specific world position.
 * <p>
 * Consulted by {@link MultiblockAmbiguityResolver} before falling back to priority order - never the
 * only source of truth, always an override on top. Persisted like {@link
 * net.astronomy.multilib.core.tracking.WorldMultiblockTracker} (this is real per-world gameplay state,
 * not transient session data), keyed by {@link BlockPos} only (not per-dimension internally - callers
 * get a separate instance per {@link ServerLevel} via {@link #get}, same as every other per-level
 * {@link SavedData} in this mod).
 */
public final class MultiblockPreferenceTracker extends SavedData {
    private static final String DATA_NAME = "multilib_preferences";

    private final Map<BlockPos, ResourceLocation> preferences = new HashMap<>();

    public static MultiblockPreferenceTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        MultiblockPreferenceTracker::new,
                        MultiblockPreferenceTracker::load,
                        null
                ),
                DATA_NAME
        );
    }

    public void set(BlockPos pos, ResourceLocation definitionId) {
        preferences.put(pos.immutable(), definitionId);
        setDirty();
    }

    /** Hard cap on {@link #setForConnectedRegion} so binding a preference on a huge connected same-block area (e.g. plain stone) can't turn one wrench click into an unbounded world scan. */
    private static final int MAX_CONNECTED_REGION_BLOCKS = 4096;

    /**
     * Like {@link #set}, but also propagates the same binding to every block reachable from
     * {@code origin} through other blocks of the exact same {@link Block} type (6-directional flood
     * fill, capped at {@link #MAX_CONNECTED_REGION_BLOCKS}). A structure built entirely (or partly) out
     * of one block type - that block's own activation/core symbol included, e.g. a shapeless structure
     * whose solid-fill body is a single material - is ambiguous at EVERY one of those positions
     * individually, not just whichever one the player happened to right-click with the preference
     * wrench: without this, the ghost overlay could still flip to a competing definition the moment a
     * later formation check runs against any other block of the same structure that never got its own
     * binding. Validity doesn't need re-checking per position: {@link MultiblockRegistry#getCandidatesFor}
     * is a pure function of block type, so whatever made {@code definitionId} valid at {@code origin}
     * (see {@code MultiLib#setPreferredDefinition}'s own validation) is equally valid at every other
     * position sharing that block type.
     */
    public void setForConnectedRegion(ServerLevel level, BlockPos origin, ResourceLocation definitionId) {
        Block originBlock = level.getBlockState(origin).getBlock();
        BlockPos originImmutable = origin.immutable();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(originImmutable);
        queue.add(originImmutable);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            preferences.put(current, definitionId);

            for (Direction dir : Direction.values()) {
                if (visited.size() >= MAX_CONNECTED_REGION_BLOCKS) break;
                BlockPos next = current.relative(dir).immutable();
                if (visited.contains(next)) continue;
                if (level.getBlockState(next).getBlock() != originBlock) continue;
                visited.add(next);
                queue.add(next);
            }
        }
        setDirty();
    }

    public void clear(BlockPos pos) {
        if (preferences.remove(pos) != null) setDirty();
    }

    public Optional<ResourceLocation> get(BlockPos pos) {
        return Optional.ofNullable(preferences.get(pos));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, ResourceLocation> entry : preferences.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", entry.getKey().asLong());
            entryTag.putString("definition", entry.getValue().toString());
            list.add(entryTag);
        }
        tag.put("preferences", list);
        return tag;
    }

    public static MultiblockPreferenceTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        MultiblockPreferenceTracker tracker = new MultiblockPreferenceTracker();
        ListTag list = tag.getList("preferences", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entryTag.getString("definition"));
            if (id == null) continue; // corrupted/foreign entry - skip rather than fail the whole load
            tracker.preferences.put(BlockPos.of(entryTag.getLong("pos")), id);
        }
        return tracker;
    }
}

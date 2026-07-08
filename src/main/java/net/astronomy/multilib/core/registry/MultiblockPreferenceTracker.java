package net.astronomy.multilib.core.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

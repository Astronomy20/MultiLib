package net.astronomy.multilib.core.tracking;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Long-term "has this player ever reached this state on this definition, and when" memory - survives
 * the underlying {@link MultiblockInstance} being broken and reformed, unlike
 * {@link WorldMultiblockTracker} which only tracks currently-live instances. Always persisted on the
 * overworld regardless of which dimension the multiblock actually formed in, since progression is
 * conceptually per-player, not per-dimension.
 * <p>
 * General-purpose record-keeping - exposed via {@link net.astronomy.multilib.api.MultiLibAPI} for mod
 * developers and integrations that want an "ever/last reached" answer. Note {@code compat/ftbquests}'
 * {@code MultiblockTask} deliberately does NOT use this for its completion check: a permanently-standing
 * historical record would let a quest re-complete instantly on reset, or "complete" a task for a
 * structure that's since been broken. It reacts to the live formation/state-change events instead - see
 * {@code MultiblockQuestEventListener}.
 */
public class MultiblockProgressionTracker extends SavedData {
    private static final String DATA_NAME = "multilib_progression";

    // player -> definitionId -> stateId -> tick last reached
    private final Map<UUID, Map<ResourceLocation, Map<ResourceLocation, Long>>> progress = new HashMap<>();

    public static MultiblockProgressionTracker get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        MultiblockProgressionTracker::new,
                        MultiblockProgressionTracker::load,
                        null
                ),
                DATA_NAME
        );
    }

    /** Records that {@code player} reached {@code stateId} for {@code definitionId} at {@code tick}. */
    public void recordStateReached(UUID player, ResourceLocation definitionId, ResourceLocation stateId, long tick) {
        Long previous = progress
                .computeIfAbsent(player, k -> new HashMap<>())
                .computeIfAbsent(definitionId, k -> new HashMap<>())
                .put(stateId, tick);
        if (previous == null || previous != tick) setDirty();
    }

    /** @param stateId if null, checks whether ANY state was ever reached; otherwise checks that specific state. */
    public boolean hasReached(UUID player, ResourceLocation definitionId, @Nullable ResourceLocation stateId) {
        Map<ResourceLocation, Long> byState = progress.getOrDefault(player, Map.of()).get(definitionId);
        if (byState == null) return false;
        return stateId == null || byState.containsKey(stateId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersList = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, Map<ResourceLocation, Long>>> playerEntry : progress.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.put("player", NbtUtils.createUUID(playerEntry.getKey()));

            ListTag definitionsList = new ListTag();
            for (Map.Entry<ResourceLocation, Map<ResourceLocation, Long>> defEntry : playerEntry.getValue().entrySet()) {
                CompoundTag defTag = new CompoundTag();
                defTag.putString("definition", defEntry.getKey().toString());

                ListTag statesList = new ListTag();
                for (Map.Entry<ResourceLocation, Long> stateEntry : defEntry.getValue().entrySet()) {
                    CompoundTag stateTag = new CompoundTag();
                    stateTag.putString("state", stateEntry.getKey().toString());
                    stateTag.putLong("tick", stateEntry.getValue());
                    statesList.add(stateTag);
                }
                defTag.put("states", statesList);
                definitionsList.add(defTag);
            }
            playerTag.put("definitions", definitionsList);
            playersList.add(playerTag);
        }
        tag.put("players", playersList);
        return tag;
    }

    public static MultiblockProgressionTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        MultiblockProgressionTracker tracker = new MultiblockProgressionTracker();

        ListTag playersList = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerTag = playersList.getCompound(i);
            UUID player;
            try {
                player = NbtUtils.loadUUID(playerTag.get("player"));
            } catch (Exception e) {
                continue;
            }

            Map<ResourceLocation, Map<ResourceLocation, Long>> byDefinition = new HashMap<>();
            ListTag definitionsList = playerTag.getList("definitions", Tag.TAG_COMPOUND);
            for (int j = 0; j < definitionsList.size(); j++) {
                CompoundTag defTag = definitionsList.getCompound(j);
                ResourceLocation definitionId = ResourceLocation.tryParse(defTag.getString("definition"));
                if (definitionId == null) continue;

                Map<ResourceLocation, Long> byState = new HashMap<>();
                ListTag statesList = defTag.getList("states", Tag.TAG_COMPOUND);
                for (int k = 0; k < statesList.size(); k++) {
                    CompoundTag stateTag = statesList.getCompound(k);
                    ResourceLocation stateId = ResourceLocation.tryParse(stateTag.getString("state"));
                    if (stateId == null) continue;
                    byState.put(stateId, stateTag.getLong("tick"));
                }
                byDefinition.put(definitionId, byState);
            }
            tracker.progress.put(player, byDefinition);
        }

        return tracker;
    }
}

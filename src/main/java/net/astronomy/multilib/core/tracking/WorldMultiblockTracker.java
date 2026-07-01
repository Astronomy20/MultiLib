package net.astronomy.multilib.core.tracking;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.callback.MultiblockAmbientContext;
import net.astronomy.multilib.api.callback.MultiblockTickContext;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class WorldMultiblockTracker extends SavedData {
    private static final String DATA_NAME = "multilib_tracker";

    private final Map<UUID, MultiblockInstance> instancesById = new HashMap<>();
    private final Map<BlockPos, Set<UUID>> positionIndex = new HashMap<>();
    private final List<MultiblockInstance> tickableInstances = new ArrayList<>();
    private final List<MultiblockInstance> ambientInstances = new ArrayList<>();
    private final Map<UUID, Long> ambientTickCounters = new HashMap<>();
    private long currentTick = 0L;

    public static WorldMultiblockTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WorldMultiblockTracker::new,
                        WorldMultiblockTracker::load,
                        null
                ),
                DATA_NAME
        );
    }

    public void register(MultiblockInstance instance, MultiblockDefinition definition) {
        instancesById.put(instance.getId(), instance);
        for (BlockPos pos : instance.getPositions()) {
            positionIndex.computeIfAbsent(pos, k -> new HashSet<>()).add(instance.getId());
        }
        if (definition.hasTickCallback()) tickableInstances.add(instance);
        if (definition.hasAmbientCallback()) ambientInstances.add(instance);
        setDirty();
    }

    public void unregister(UUID id) {
        MultiblockInstance instance = instancesById.remove(id);
        if (instance == null) return;
        for (BlockPos pos : instance.getPositions()) {
            Set<UUID> uuids = positionIndex.get(pos);
            if (uuids != null) {
                uuids.remove(id);
                if (uuids.isEmpty()) positionIndex.remove(pos);
            }
        }
        tickableInstances.removeIf(i -> i.getId().equals(id));
        ambientInstances.removeIf(i -> i.getId().equals(id));
        ambientTickCounters.remove(id);
        setDirty();
    }

    public Set<MultiblockInstance> getInstancesAt(BlockPos pos) {
        Set<UUID> ids = positionIndex.getOrDefault(pos, Set.of());
        return ids.stream()
                .map(instancesById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Optional<MultiblockInstance> getById(UUID id) {
        return Optional.ofNullable(instancesById.get(id));
    }

    public Collection<MultiblockInstance> getAllInstances() {
        return Collections.unmodifiableCollection(instancesById.values());
    }

    public void tick(ServerLevel level) {
        currentTick++;

        for (MultiblockInstance instance : tickableInstances) {
            MultiblockRegistry.get(instance.getDefinitionId()).ifPresent(def ->
                    def.getTickCallback().ifPresent(cb -> {
                        MultiblockContext ctx = new MultiblockContext(level, instance, def);
                        cb.onTick(new MultiblockTickContext(ctx));
                    })
            );
        }

        for (MultiblockInstance instance : ambientInstances) {
            MultiblockRegistry.get(instance.getDefinitionId()).ifPresent(def ->
                    def.getAmbientCallback().ifPresent(cb -> {
                        long lastTick = ambientTickCounters.getOrDefault(instance.getId(), 0L);
                        if (currentTick - lastTick >= def.getAmbientIntervalTicks()) {
                            ambientTickCounters.put(instance.getId(), currentTick);
                            MultiblockContext ctx = new MultiblockContext(level, instance, def);
                            cb.onAmbient(new MultiblockAmbientContext(ctx));
                        }
                    })
            );
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        List<MultiblockInstance> snapshot = new ArrayList<>(instancesById.values());
        ListTag list = new ListTag();
        for (MultiblockInstance instance : snapshot) {
            list.add(instance.save());
        }
        tag.put("instances", list);
        return tag;
    }

    public static WorldMultiblockTracker load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        WorldMultiblockTracker tracker = new WorldMultiblockTracker();
        ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Optional<MultiblockInstance> loaded = MultiblockInstance.load(list.getCompound(i));
            if (loaded.isEmpty()) {
                MultiLib.LOGGER.warn("[MultiLib] Discarding orphaned MultiblockInstance: definition not found or data corrupted");
                continue;
            }
            MultiblockInstance instance = loaded.get();
            MultiblockDefinition def = MultiblockRegistry.get(instance.getDefinitionId()).orElse(null);
            if (def != null) {
                tracker.register(instance, def);
            }
        }
        return tracker;
    }
}

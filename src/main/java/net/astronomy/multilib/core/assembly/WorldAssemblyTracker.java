package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-{@link ServerLevel} {@link SavedData} that owns the {@link AssemblyInstance}s. It stores only
 * the assemblies (member UUIDs); the assembly's block positions are always derived from the member
 * {@code MultiblockInstance}s via {@link WorldMultiblockTracker}, never duplicated here. The
 * {@code memberToAssembly} index gives O(1) "which assembly is this member in".
 */
public class WorldAssemblyTracker extends SavedData {
    private static final String DATA_NAME = "multilib_assembly_tracker";

    private final Map<UUID, AssemblyInstance> byId = new HashMap<>();
    private final Map<UUID, UUID> memberToAssembly = new HashMap<>();

    public static WorldAssemblyTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldAssemblyTracker::new, WorldAssemblyTracker::load, null),
                DATA_NAME);
    }

    public void register(AssemblyInstance assembly) {
        byId.put(assembly.getId(), assembly);
        for (UUID memberId : assembly.allMemberIds()) {
            memberToAssembly.put(memberId, assembly.getId());
        }
        setDirty();
    }

    public void unregister(UUID assemblyId) {
        AssemblyInstance removed = byId.remove(assemblyId);
        if (removed == null) return;
        for (UUID memberId : removed.allMemberIds()) {
            memberToAssembly.remove(memberId);
        }
        setDirty();
    }

    public void indexMember(UUID memberId, UUID assemblyId) {
        memberToAssembly.put(memberId, assemblyId);
        setDirty();
    }

    public void unindexMember(UUID memberId) {
        memberToAssembly.remove(memberId);
        setDirty();
    }

    public Optional<AssemblyInstance> getById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<AssemblyInstance> getByMember(UUID memberId) {
        UUID assemblyId = memberToAssembly.get(memberId);
        return assemblyId == null ? Optional.empty() : Optional.ofNullable(byId.get(assemblyId));
    }

    public boolean isMemberClaimed(UUID memberId) {
        return memberToAssembly.containsKey(memberId);
    }

    public Collection<AssemblyInstance> getAll() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** The assembly whose member sub-structure occupies {@code pos}, if any. */
    public Optional<AssemblyInstance> getAssemblyAt(ServerLevel level, BlockPos pos) {
        Set<MultiblockInstance> instances = WorldMultiblockTracker.get(level).getInstancesAt(pos);
        for (MultiblockInstance mi : instances) {
            Optional<AssemblyInstance> a = getByMember(mi.getId());
            if (a.isPresent()) return a;
        }
        return Optional.empty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AssemblyInstance a : new ArrayList<>(byId.values())) {
            list.add(a.save());
        }
        tag.put("assemblies", list);
        return tag;
    }

    public static WorldAssemblyTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldAssemblyTracker tracker = new WorldAssemblyTracker();
        ListTag list = tag.getList("assemblies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Optional<AssemblyInstance> loaded = AssemblyInstance.load(list.getCompound(i));
            if (loaded.isEmpty()) {
                MultiLib.LOGGER.warn("[MultiLib] Discarding corrupt AssemblyInstance on load");
                continue;
            }
            AssemblyInstance a = loaded.get();
            // AssemblyRegistry may not yet contain the definition at load time; keep the instance
            // anyway — the matcher/break handler reconcile it against live members later.
            tracker.byId.put(a.getId(), a);
            for (UUID memberId : a.allMemberIds()) {
                tracker.memberToAssembly.put(memberId, a.getId());
            }
        }
        return tracker;
    }
}

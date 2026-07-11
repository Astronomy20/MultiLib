package net.astronomy.multilib.api.assembly;

import net.astronomy.multilib.MultiLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A formed assembly in the world. Unlike {@link net.astronomy.multilib.api.instance.MultiblockInstance}
 * (immutable), an assembly is mutable: members join and leave over its lifetime and its state changes.
 * It owns no blocks — it references its member {@code MultiblockInstance}s by UUID, keyed by role.
 */
public final class AssemblyInstance {
    private final UUID id;
    private final ResourceLocation definitionId;
    private final Map<String, Set<UUID>> members = new LinkedHashMap<>();
    private final Optional<UUID> formedBy;
    private AssemblyState state;

    public AssemblyInstance(UUID id, ResourceLocation definitionId, Optional<UUID> formedBy) {
        this.id = id;
        this.definitionId = definitionId;
        this.formedBy = formedBy;
        this.state = StandardAssemblyState.FORMED;
    }

    public UUID getId() { return id; }
    public ResourceLocation getDefinitionId() { return definitionId; }
    public Optional<UUID> getFormedBy() { return formedBy; }
    public AssemblyState getState() { return state; }
    public void setState(AssemblyState state) { this.state = state; }

    /** UUIDs of the member instances filling {@code role}; empty if none. */
    public Set<UUID> getMembers(String role) {
        return members.getOrDefault(role, Set.of());
    }

    public Map<String, Set<UUID>> getAllMembers() {
        return members;
    }

    /** Every member UUID across all roles. */
    public Set<UUID> allMemberIds() {
        Set<UUID> all = new HashSet<>();
        for (Set<UUID> s : members.values()) all.addAll(s);
        return all;
    }

    public boolean addMember(String role, UUID memberId) {
        return members.computeIfAbsent(role, k -> new HashSet<>()).add(memberId);
    }

    /** Removes a member from any role. @return the role it was in, or empty if it wasn't a member. */
    public Optional<String> removeMember(UUID memberId) {
        for (Map.Entry<String, Set<UUID>> e : members.entrySet()) {
            if (e.getValue().remove(memberId)) {
                if (e.getValue().isEmpty()) members.remove(e.getKey());
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public int memberCount(String role) {
        return members.getOrDefault(role, Set.of()).size();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("id", NbtUtils.createUUID(id));
        tag.putString("definition", definitionId.toString());
        tag.putString("state", state.getId());
        formedBy.ifPresent(uuid -> tag.put("formedBy", NbtUtils.createUUID(uuid)));

        CompoundTag membersTag = new CompoundTag();
        members.forEach((role, ids) -> {
            ListTag list = new ListTag();
            for (UUID uuid : ids) list.add(NbtUtils.createUUID(uuid));
            membersTag.put(role, list);
        });
        tag.put("members", membersTag);
        return tag;
    }

    public static Optional<AssemblyInstance> load(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_INT_ARRAY)) return Optional.empty();
        if (!tag.contains("definition", Tag.TAG_STRING)) return Optional.empty();

        UUID id;
        try {
            id = NbtUtils.loadUUID(tag.get("id"));
        } catch (Exception e) {
            MultiLib.LOGGER.warn("[MultiLib] Failed to load AssemblyInstance UUID", e);
            return Optional.empty();
        }

        ResourceLocation definitionId = ResourceLocation.tryParse(tag.getString("definition"));
        if (definitionId == null) return Optional.empty();

        Optional<UUID> formedBy = Optional.empty();
        if (tag.contains("formedBy", Tag.TAG_INT_ARRAY)) {
            try {
                formedBy = Optional.of(NbtUtils.loadUUID(tag.get("formedBy")));
            } catch (Exception e) {
                MultiLib.LOGGER.warn("[MultiLib] Failed to load AssemblyInstance formedBy UUID", e);
            }
        }

        AssemblyInstance instance = new AssemblyInstance(id, definitionId, formedBy);
        instance.setState(StandardAssemblyState.byId(tag.getString("state")));

        CompoundTag membersTag = tag.getCompound("members");
        Map<String, Set<UUID>> loaded = new HashMap<>();
        for (String role : membersTag.getAllKeys()) {
            ListTag list = membersTag.getList(role, Tag.TAG_INT_ARRAY);
            Set<UUID> ids = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                try {
                    ids.add(NbtUtils.loadUUID(list.get(i)));
                } catch (Exception e) {
                    MultiLib.LOGGER.warn("[MultiLib] Skipping bad member UUID in assembly {}", id);
                }
            }
            if (!ids.isEmpty()) loaded.put(role, ids);
        }
        instance.members.putAll(loaded);
        return Optional.of(instance);
    }
}

package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable backing store for {@link MultiblockDevListSessionRegistry}: which dev-block's HUD list (if any)
 * each player had shown, surviving a relog or a full world restart - not just the current play session.
 * Stored once per world (always under the overworld's own data storage, since a player's UUID is stable
 * regardless of which dimension they were actually in), same {@code SavedData} pattern as
 * {@code WorldMultiblockTracker}.
 */
public class MultiblockDevListSessionStorage extends SavedData {

    private static final String DATA_NAME = "multilib_dev_list_sessions";

    private final Map<UUID, MultiblockDevListSessionRegistry.Session> sessions = new HashMap<>();

    public static MultiblockDevListSessionStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MultiblockDevListSessionStorage::new, MultiblockDevListSessionStorage::load, null),
                DATA_NAME);
    }

    public void setSession(UUID playerId, @Nullable MultiblockDevListSessionRegistry.Session session) {
        if (session == null) {
            sessions.remove(playerId);
        } else {
            sessions.put(playerId, session);
        }
        setDirty();
    }

    public @Nullable MultiblockDevListSessionRegistry.Session getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, MultiblockDevListSessionRegistry.Session> entry : sessions.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("player", entry.getKey());
            entryTag.putString("dimension", entry.getValue().dimension().location().toString());
            entryTag.put("pos", NbtUtils.writeBlockPos(entry.getValue().devBlockPos()));
            list.add(entryTag);
        }
        tag.put("sessions", list);
        return tag;
    }

    public static MultiblockDevListSessionStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        MultiblockDevListSessionStorage storage = new MultiblockDevListSessionStorage();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID playerId = entryTag.getUUID("player");
            ResourceLocation dimId = ResourceLocation.tryParse(entryTag.getString("dimension"));
            Optional<BlockPos> pos = NbtUtils.readBlockPos(entryTag, "pos");
            if (dimId == null || pos.isEmpty()) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimId);
            storage.sessions.put(playerId, new MultiblockDevListSessionRegistry.Session(dimension, pos.get()));
        }
        return storage;
    }
}

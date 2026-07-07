package net.astronomy.multilib.compat.jade;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.hud.HudContext;
import net.astronomy.multilib.api.hud.HudEntry;
import net.astronomy.multilib.api.hud.MultiblockHudRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/**
 * Server-side half of the Jade bridge: runs {@link MultiblockHudRegistry#gatherEntries}/
 * {@link MultiblockHudRegistry#gatherUnformedEntries} for whatever block Jade is currently probing and
 * writes the serialized {@link HudEntry} list into Jade's server-data tag, which Jade ships to the
 * client for {@link MultiblockJadeComponentProvider} to read back and render.
 */
public final class MultiblockJadeServerProvider implements IServerDataProvider<BlockAccessor> {

    public static final MultiblockJadeServerProvider INSTANCE = new MultiblockJadeServerProvider();

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "hud_server_data");

    private MultiblockJadeServerProvider() {}

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        if (!(accessor.getPlayer() instanceof ServerPlayer player)) return;
        BlockPos pos = accessor.getPosition();

        List<HudEntry> entries = HudContext.at(level, pos, player)
                .map(MultiblockHudRegistry::gatherEntries)
                .orElseGet(() -> MultiblockHudRegistry.gatherUnformedEntries(level, pos, player));
        if (entries.isEmpty()) return;

        HolderLookup.Provider registries = level.registryAccess();
        ListTag list = new ListTag();
        for (HudEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entry.save(entryTag, registries);
            list.add(entryTag);
        }
        data.put("entries", list);
    }
}

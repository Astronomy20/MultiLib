package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.network.DevListVisibilityPacket;
import net.astronomy.multilib.network.DevScanResultPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks, per player, which single Multiblock Dev Block (if any) currently has its scoreboard-style HUD
 * list shown - the vanilla-scoreboard-sidebar-like overlay meant to be visible without ever opening the
 * block's GUI (see phase-10 follow-up: "vuoi vedere i blocchi scansionati senza dover riaprire il
 * dev-block ogni volta"). Deliberately a single slot per player rather than a set: showing the list for a
 * different dev-block silently replaces whichever one was previously shown, which is exactly how the
 * exclusivity ("solo uno abbia la lista attiva") was meant to work - achieved for free by only ever
 * keeping one active target, no explicit cross-block coordination needed.
 * <p>
 * {@link #ACTIVE} itself is only ever the current play session's cache (cleared on logout, same as
 * {@code MultiblockDevTagSessionRegistry}), but every change is mirrored into
 * {@link MultiblockDevListSessionStorage}, which actually persists to the world - so a player who had a
 * list shown gets it back automatically on rejoining, not just within the same session.
 */
public final class MultiblockDevListSessionRegistry {

    private MultiblockDevListSessionRegistry() {
    }

    public record Session(ResourceKey<Level> dimension, BlockPos devBlockPos) {
    }

    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Toggles the HUD list for {@code devBlockPos}: activates it (replacing whatever was previously
     * active for this player) if it wasn't already the active target, or deactivates it if it was.
     *
     * @return the new active session, or empty if the list was just hidden.
     */
    public static Optional<Session> toggle(MinecraftServer server, UUID playerId, ResourceKey<Level> dimension, BlockPos devBlockPos) {
        Session candidate = new Session(dimension, devBlockPos);
        Optional<Session> result;
        if (candidate.equals(ACTIVE.get(playerId))) {
            ACTIVE.remove(playerId);
            result = Optional.empty();
        } else {
            ACTIVE.put(playerId, candidate);
            result = Optional.of(candidate);
        }
        MultiblockDevListSessionStorage.get(server).setSession(playerId, result.orElse(null));
        return result;
    }

    public static Optional<Session> get(UUID playerId) {
        return Optional.ofNullable(ACTIVE.get(playerId));
    }

    /**
     * Clears {@code playerId}'s session (in-memory and persisted) only if it currently points at exactly
     * {@code dimension}/{@code devBlockPos} - used when that dev-block is broken, so a player watching it
     * doesn't keep seeing a frozen HUD pointed at a block that no longer exists.
     *
     * @return whether it was actually active (and therefore just cleared) - callers use this to decide
     * whether to bother telling the client to hide the HUD.
     */
    public static boolean clearIfActive(MinecraftServer server, UUID playerId, ResourceKey<Level> dimension, BlockPos devBlockPos) {
        Session current = ACTIVE.get(playerId);
        if (current == null || !current.dimension().equals(dimension) || !current.devBlockPos().equals(devBlockPos)) {
            return false;
        }
        ACTIVE.remove(playerId);
        MultiblockDevListSessionStorage.get(server).setSession(playerId, null);
        return true;
    }

    /** In-memory only - the persisted copy in {@link MultiblockDevListSessionStorage} is left untouched, so the next login restores it. */
    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    @EventBusSubscriber(modid = MultiLib.MODID)
    static final class CleanupListener {
        private CleanupListener() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            clear(event.getEntity().getUUID());
        }

        /**
         * Restores whichever dev-block's list this player had shown before they last left (or the server
         * last stopped), and immediately resumes the HUD for them - without this, the persisted session
         * would just sit in {@link MultiblockDevListSessionStorage} unused until the player happened to
         * toggle the list again themselves.
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Session session = MultiblockDevListSessionStorage.get(server).getSession(player.getUUID());
            if (session == null) return;

            ServerLevel level = server.getLevel(session.dimension());
            if (level == null || !(level.getBlockEntity(session.devBlockPos()) instanceof MultiblockDevBlockEntity be)) {
                // The dev-block is gone (broken while offline, or its dimension no longer exists) -
                // don't resurrect a HUD pointed at nothing.
                MultiblockDevListSessionStorage.get(server).setSession(player.getUUID(), null);
                return;
            }

            ACTIVE.put(player.getUUID(), session);
            PacketDistributor.sendToPlayer(player, new DevListVisibilityPacket(true, session.devBlockPos()));
            be.getLastScan().ifPresent(scan -> PacketDistributor.sendToPlayer(
                    player, new DevScanResultPacket(session.devBlockPos(), true, "", scan)));
        }
    }
}

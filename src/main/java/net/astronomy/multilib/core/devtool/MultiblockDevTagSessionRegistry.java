package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.MultiLib;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient per-player session tracking the last successful area scan ("Detect") performed by a
 * dev-block's menu, so the global dev-wrench tagging listener ({@code MultiblockDevTagHandler})
 * can tell whether a clicked position falls inside that area without having to reach back into a
 * specific {@code MultiblockDevBlockEntity} instance.
 * <p>
 * Deliberately <b>not</b> persisted and <b>not</b> tied to the block entity's lifecycle - this mirrors
 * the {@code OverlayRequestHandler.PLAYER_STATES} pattern (see that class): a plain per-player map,
 * cleared on logout, rebuilt from scratch on the next successful Detect. If a player has multiple
 * dev-blocks placed, only the most recently detected area is tracked - tagging always targets "whatever
 * was last scanned" (documented limitation, see phase-10 design doc).
 */
public final class MultiblockDevTagSessionRegistry {

    private MultiblockDevTagSessionRegistry() {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /**
     * @param devBlockPos position of the dev-block whose menu produced this scan; used by the tag
     *                    handler to locate the owning {@code MultiblockDevBlockEntity} to actually
     *                    apply the tag to.
     * @param boxMin      inclusive minimum corner of the last scanned bounding box (world space).
     * @param boxMax      inclusive maximum corner of the last scanned bounding box (world space).
     */
    public record Session(BlockPos devBlockPos, BlockPos boxMin, BlockPos boxMax) {
    }

    public static void set(UUID playerId, Session session) {
        SESSIONS.put(playerId, session);
    }

    public static Optional<Session> get(UUID playerId) {
        return Optional.ofNullable(SESSIONS.get(playerId));
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    /**
     * Drops a disconnecting player's session immediately, instead of letting it linger indefinitely -
     * there's no periodic sweep for this map (unlike {@code OverlayRequestHandler}'s duration-based
     * timeout), so logout is the only cleanup trigger.
     */
    @EventBusSubscriber(modid = MultiLib.MODID)
    static final class CleanupListener {
        private CleanupListener() {
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            clear(event.getEntity().getUUID());
        }
    }
}

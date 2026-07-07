package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivers multiblock-loading chat errors (see {@code MultiblockBuilder}'s validation) to players.
 * An immediate broadcast alone isn't enough: the initial datapack load happens before any player has
 * joined the world, so a broadcast attempted right then reaches nobody. This both broadcasts
 * immediately to whoever is already online (still useful mid-session, e.g. after a {@code /reload})
 * and queues the same message so it's also shown to every player as soon as they join afterward.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class MultiblockLoadErrorNotifier {
    private static final List<String> PENDING = new ArrayList<>();

    private MultiblockLoadErrorNotifier() {}

    public static void notify(String message) {
        PENDING.add(message);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(styled(message), false);
        }
    }

    /**
     * Clears queued messages - called at the start of each datapack reload (see
     * {@code MultiblockJsonLoader}), so an error from a since-fixed definition doesn't keep
     * resurfacing to every player who joins after the fix.
     */
    public static void clear() {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (String message : List.copyOf(PENDING)) {
            player.sendSystemMessage(styled(message));
        }
    }

    private static Component styled(String message) {
        return Component.literal("[MultiLib] " + message).withStyle(ChatFormatting.RED);
    }
}

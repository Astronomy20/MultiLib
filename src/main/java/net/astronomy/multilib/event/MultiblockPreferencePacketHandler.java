package net.astronomy.multilib.event;

import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.network.PreferredDefinitionResultPacket;
import net.astronomy.multilib.network.RequestSetPreferredDefinitionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handling for the preference wrench's single request packet (see {@code core.preference}).
 * The client already filtered/validated the candidate list locally before ever showing the picker (see
 * {@code MultiblockAmbiguityResolver#candidatesAt}, callable from client code precisely so this stays
 * cheap and consistent) - this handler re-validates server-side anyway via
 * {@link MultiLibAPI#setPreferredDefinition} rather than trusting the client's selection blindly, since
 * the block at {@code pos} could have changed in the time between the client opening the picker and the
 * player clicking a choice.
 */
public final class MultiblockPreferencePacketHandler {

    private MultiblockPreferencePacketHandler() {}

    public static void handleSetRequest(RequestSetPreferredDefinitionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            boolean accepted = MultiLibAPI.setPreferredDefinition(level, packet.pos(), packet.definitionId());
            PacketDistributor.sendToPlayer(player,
                    new PreferredDefinitionResultPacket(packet.pos(), accepted, packet.definitionId()));
        });
    }
}

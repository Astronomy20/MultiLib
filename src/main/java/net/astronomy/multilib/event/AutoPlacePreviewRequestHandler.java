package net.astronomy.multilib.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.RequestAutoPlacePreviewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers the client's hover-driven "can my held item be auto-placed here" query with the single
 * position where the next Ctrl+Right-click would place the held item (see
 * {@link AutoPlacePreviewDataPacket}) - not every remaining position of that type, so the preview
 * matches what one auto-place click actually does. Purely read-only - no blocks are placed and no
 * items are consumed.
 */
public class AutoPlacePreviewRequestHandler {

    public static void handleRequest(RequestAutoPlacePreviewPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (!packet.active()) {
                PacketDistributor.sendToPlayer(player, new AutoPlacePreviewDataPacket(List.of()));
                return;
            }

            sendPreviewUpdate(player, player.serverLevel(), packet.corePos());
        });
    }

    /**
     * (Re)computes and sends the auto-place preview for {@code corePos} right away, instead of
     * waiting for the client's next hover poll - used right after {@link AutoPlaceRequestHandler}
     * places a block, so the overlay immediately jumps to the next missing position (or clears once
     * the structure is complete) rather than showing the just-filled spot until the next poll tick.
     */
    static void sendPreviewUpdate(ServerPlayer player, ServerLevel level, BlockPos corePos) {
        MultiblockDefinition definition = AutoPlaceRequestHandler.findAutoPlaceDefinitionAt(level, corePos);
        if (definition == null || !definition.isAutoPlaceOverlay() || definition.getLayers().isEmpty()) {
            PacketDistributor.sendToPlayer(player, new AutoPlacePreviewDataPacket(List.of()));
            return;
        }

        ItemStack heldStack = player.getMainHandItem();
        if (heldStack.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new AutoPlacePreviewDataPacket(List.of()));
            return;
        }
        Item heldItem = heldStack.getItem();

        List<AutoPlaceRequestHandler.Candidate> candidates =
                AutoPlaceRequestHandler.computeCandidates(player, level, corePos, definition);
        if (candidates == null || candidates.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new AutoPlacePreviewDataPacket(List.of()));
            return;
        }

        List<GhostBlockData> preview = new ArrayList<>();
        for (AutoPlaceRequestHandler.Candidate candidate : candidates) {
            if (candidate.state().getBlock().asItem() != heldItem) continue;
            preview.add(new GhostBlockData(candidate.pos(), candidate.state(), GhostBlockData.Status.PLACEABLE));
            break; // Only the next position an auto-place click would actually fill.
        }

        PacketDistributor.sendToPlayer(player, new AutoPlacePreviewDataPacket(preview));
    }
}

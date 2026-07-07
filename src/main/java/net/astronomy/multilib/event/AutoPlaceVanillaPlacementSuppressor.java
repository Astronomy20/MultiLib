package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Cancels the server-side {@link PlayerInteractEvent.RightClickBlock} that the vanilla client always
 * fires alongside a Ctrl+Right-click on an auto-place core - the client's own cancellation of that
 * event (see {@code AutoPlaceInputHandler}) only stops client-side prediction, not the
 * {@code ServerboundUseItemOnPacket} the game unconditionally sends, so without this the server would
 * go on to place the held item's block wherever the player was aiming, on top of whatever the
 * auto-place system placed via {@link AutoPlaceRequestHandler}.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public class AutoPlaceVanillaPlacementSuppressor {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (AutoPlaceRequestHandler.consumeVanillaSuppression(player.getUUID(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}

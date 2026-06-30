package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.RequestOverlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class GhostOverlayInputHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        if (!event.getItemStack().isEmpty()) return;

        Level level = event.getLevel();
        if (!(level instanceof ClientLevel)) return;

        BlockPos pos = event.getPos();
        Block block = level.getBlockState(pos).getBlock();

        // The ghost overlay is only previewable from the core block, not the activation block (when
        // a structure splits them) — clicking any other block that merely happens to be a body
        // block of some other registered structure must never trigger it.
        boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
            .anyMatch(def -> def.matchesCore(level.getBlockState(pos)));

        if (!isTrigger) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        // Horizontal orientation follows the direction the player is facing, not the face of the
        // block they happened to click — clicking the west wall of the core while facing north must
        // still preview the structure facing north. The UP/DOWN flip trigger is the one case that
        // genuinely depends on which face was clicked (there's no "vertical" player facing), so that
        // part still reads the clicked face.
        net.minecraft.core.Direction face = event.getFace();
        net.minecraft.core.Direction orientationFace =
                (face == net.minecraft.core.Direction.UP || face == net.minecraft.core.Direction.DOWN)
                        ? face
                        : player.getDirection();
        PacketDistributor.sendToServer(new RequestOverlayPacket(pos, 0, orientationFace != null ? orientationFace.ordinal() : -1));
    }
}

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

        // Previewable from either the core or the activation block (when a structure splits them -
        // see OverlayRequestHandler#resolveAnchorSymbol, which detects the structure's placed
        // orientation from ground truth when anchored on the activation symbol specifically, rather
        // than guessing from player facing). Clicking any other block that merely happens to be a
        // body block of some other registered structure must never trigger it. Also requires
        // isGhostOverlayEnabled() - a definition that opted out never previews, no matter which
        // symbol was clicked.
        boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
            .anyMatch(def -> def.isGhostOverlayEnabled() && def.matchesActivationOrCore(level.getBlockState(pos)));

        if (!isTrigger) return;

        // A block that's a real multiblock's core can also fall inside a Multiblock Dev Block's active
        // area preview (the "Render" toggle) - that same sneak+right-click is also the dev-tool's
        // tagging gesture (see MultiblockDevTagHandler, server-side, a separate/independent listener
        // that this client-side cancel can't reach). Rather than ever pick one feature to silently win,
        // suppress the ghost overlay specifically while the dev-block's preview box covers this
        // position, so tagging isn't fighting the overlay for the same click.
        if (isWithinDevBlockPreview(pos)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        // Horizontal orientation follows the direction the player is facing, not the face of the
        // block they happened to click - clicking the west wall of the core while facing north must
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

    private static boolean isWithinDevBlockPreview(BlockPos pos) {
        net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.Box box =
                net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.get();
        if (box == null) return false;
        BlockPos min = box.min();
        BlockPos max = box.max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}

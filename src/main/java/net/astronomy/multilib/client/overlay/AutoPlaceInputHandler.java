package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.minecraft.client.gui.screens.Screen;
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

/**
 * Ctrl+Right-click triggers auto-placement on an autoPlace()-enabled core block. Ctrl is unused
 * elsewhere in this mod and doesn't collide with the ghost overlay's Shift+Right-click trigger.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class AutoPlaceInputHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Screen.hasControlDown()) return;
        Player player = event.getEntity();
        if (!event.getItemStack().isEmpty()) return;

        Level level = event.getLevel();
        if (!(level instanceof ClientLevel)) return;

        BlockPos pos = event.getPos();
        Block block = level.getBlockState(pos).getBlock();

        boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
            .anyMatch(def -> def.isAutoPlace() && def.matchesCore(level.getBlockState(pos)));

        if (!isTrigger) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        PacketDistributor.sendToServer(new RequestAutoPlacePacket(pos));
    }
}

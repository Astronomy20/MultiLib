package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.RequestAutoPlacePreviewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

/**
 * Every {@link #POLL_INTERVAL_TICKS} ticks, checks whether the player is looking at the core block of
 * an unformed, {@code autoPlaceOverlay()}-enabled structure while holding a non-empty item — if so,
 * (re)requests the preview from the server; otherwise clears it locally without a round trip. No
 * click is needed, mirroring how the ghost overlay and auto-place both key off looking at the core.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class AutoPlacePreviewHoverHandler {

    private static final int POLL_INTERVAL_TICKS = 5;
    private static int tickCounter = 0;

    // Avoids re-requesting every poll when nothing relevant changed.
    private static BlockPos lastCorePos = null;
    private static Item lastHeldItem = null;
    private static boolean wasActive = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (++tickCounter < POLL_INTERVAL_TICKS) return;
        tickCounter = 0;

        BlockPos corePos = null;
        ItemStack held = mc.player.getMainHandItem();
        Item heldItem = held.isEmpty() ? null : held.getItem();

        if (heldItem != null && mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            Block block = mc.level.getBlockState(pos).getBlock();
            boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
                    .anyMatch(def -> def.isAutoPlace() && def.isAutoPlaceOverlay()
                            && def.matchesCore(mc.level.getBlockState(pos)));
            if (isTrigger) corePos = pos;
        }

        boolean active = corePos != null;
        boolean unchanged = active == wasActive
                && Objects.equals(corePos, lastCorePos)
                && Objects.equals(heldItem, lastHeldItem);
        if (unchanged) return;

        lastCorePos = corePos;
        lastHeldItem = heldItem;
        wasActive = active;

        if (!active) {
            AutoPlacePreviewState.INSTANCE.clear();
            PacketDistributor.sendToServer(new RequestAutoPlacePreviewPacket(BlockPos.ZERO, false));
        } else {
            PacketDistributor.sendToServer(new RequestAutoPlacePreviewPacket(corePos, true));
        }
    }
}

package net.astronomy.multilib.client.overlay;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.client.MultiLibClientAPI;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Holding the auto-place modifier key (Left Ctrl by default) and Right-clicking triggers
 * auto-placement on an autoPlace()-enabled core block, placing exactly one missing block per click.
 * The modifier key is not a Controls-menu keybind - its default only changes if the integrating
 * mod's own client-side code calls {@link MultiLibClientAPI#setAutoPlaceModifierKey(int)}. The
 * default doesn't collide with the ghost overlay's Shift+Right-click trigger. Works with an item in
 * hand too - the server picks a missing position that matches the held item, so holding something
 * unrelated to the structure places nothing rather than a random block.
 * <p>
 * Holding the click down re-fires at a configurable speed relative to vanilla: vanilla only
 * re-triggers {@link PlayerInteractEvent.RightClickBlock} for a held right-click every
 * {@value #VANILLA_REPEAT_TICKS} ticks ({@code Minecraft#rightClickDelay}). Rather than piggyback on
 * that fixed cadence, only the very first press is placed from {@link #onRightClickBlock} (for
 * zero-delay feedback, matching vanilla); every repeat while still held is instead paced by
 * {@link #onClientTick} against {@link CommonConfig#AUTO_PLACE_SPEED_HELD_ITEM}/
 * {@link CommonConfig#AUTO_PLACE_SPEED_EMPTY_HAND}, so the configured speed is honored whether it's
 * faster or slower than vanilla's own rate.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class AutoPlaceInputHandler {

    private static final int VANILLA_REPEAT_TICKS = 4;

    private static boolean holding = false;
    private static int ticksSinceLastRequest = 0;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!MultiLibClientAPI.isAutoPlaceModifierDown()) return;
        Player player = event.getEntity();

        Level level = event.getLevel();
        if (!(level instanceof ClientLevel)) return;

        BlockPos pos = event.getPos();
        Block block = level.getBlockState(pos).getBlock();

        boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
            .anyMatch(def -> def.isAutoPlace() && def.matchesCore(level.getBlockState(pos)));

        if (!isTrigger) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!holding) {
            // Fresh press: place immediately, same as vanilla's own zero-delay first click. While
            // already holding, vanilla still re-fires this event on its own 4-tick cadence - don't
            // send another request for it, onClientTick alone paces the configured repeat speed.
            holding = true;
            ticksSinceLastRequest = 0;
            sendRequest(pos);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !MultiLibClientAPI.isAutoPlaceModifierDown() || !mc.options.keyUse.isDown()) {
            holding = false;
            ticksSinceLastRequest = 0;
            return;
        }

        BlockPos pos = lookingAtAutoPlaceCore(mc);
        if (pos == null) {
            holding = false;
            ticksSinceLastRequest = 0;
            return;
        }

        if (!holding) {
            // The click that started this hold already sent a request via onRightClickBlock.
            holding = true;
            ticksSinceLastRequest = 0;
            return;
        }

        int interval = repeatIntervalTicks(mc.player.getMainHandItem().isEmpty());
        if (++ticksSinceLastRequest >= interval) {
            ticksSinceLastRequest = 0;
            sendRequest(pos);
        }
    }

    private static int repeatIntervalTicks(boolean handEmpty) {
        double speed = handEmpty
                ? CommonConfig.AUTO_PLACE_SPEED_EMPTY_HAND.get()
                : CommonConfig.AUTO_PLACE_SPEED_HELD_ITEM.get();
        return Math.max(1, (int) Math.round(VANILLA_REPEAT_TICKS / speed));
    }

    private static BlockPos lookingAtAutoPlaceCore(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        boolean isTrigger = MultiblockRegistry.getCandidatesFor(block).stream()
            .anyMatch(def -> def.isAutoPlace() && def.matchesCore(mc.level.getBlockState(pos)));
        return isTrigger ? pos : null;
    }

    private static void sendRequest(BlockPos pos) {
        // If the auto-place overlay is currently showing (i.e. the player's held item has a
        // displayed target), place exactly there instead of letting the server recompute a position
        // from the player's facing at click time, which can drift a step from what was just shown.
        List<GhostBlockData> preview = AutoPlacePreviewState.INSTANCE.getBlocksToRender();
        BlockPos overlayTarget = preview.isEmpty() ? BlockPos.ZERO : preview.get(0).pos();
        boolean hasOverlayTarget = !preview.isEmpty();

        PacketDistributor.sendToServer(new RequestAutoPlacePacket(pos, overlayTarget, hasOverlayTarget));
    }
}

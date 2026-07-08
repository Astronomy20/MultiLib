package net.astronomy.multilib.client.preference;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.preference.MultiblockPreferenceToolRegistry;
import net.astronomy.multilib.core.registry.MultiblockAmbiguityResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Global right-click listener that opens the preference picker (see {@link MultiblockPreferenceScreen})
 * when the preference wrench is held and the clicked block is genuinely ambiguous - a core/activation
 * symbol for more than one registered definition (see {@link MultiblockAmbiguityResolver#candidatesAt}).
 * <p>
 * The candidate list is computed entirely client-side, no round trip needed: definitions are static,
 * both-sides-loaded registry data (the exact same check {@code GhostOverlayInputHandler} already runs
 * client-side today for its own trigger), so there's nothing server-only about "which definitions could
 * this block be". Only the actual binding (see {@code RequestSetPreferredDefinitionPacket}) needs the
 * server, since that's real per-world state.
 * <p>
 * Holding the wrench suppresses every other interaction on the clicked block, same as
 * {@code MultiblockDevTagHandler} - a wrench click is never anything other than a preference attempt,
 * even when it turns out to be a no-op (block isn't ambiguous).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class MultiblockPreferenceInputHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (MultiblockPreferenceToolRegistry.WRENCH_ITEM == null
                || player.getMainHandItem().getItem() != MultiblockPreferenceToolRegistry.WRENCH_ITEM) {
            return;
        }

        Level level = event.getLevel();
        if (!(level instanceof ClientLevel)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        BlockPos pos = event.getPos();
        List<MultiblockDefinition> candidates = MultiblockAmbiguityResolver.candidatesAt(level, pos,
                (def, state) -> def.matchesActivationOrCore(state));
        if (candidates.size() < 2) return; // nothing ambiguous here - no-op, same as a normal wrench miss

        net.minecraft.client.Minecraft.getInstance().setScreen(new MultiblockPreferenceScreen(pos, candidates));
    }
}

package net.astronomy.multilib.event;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.event.WrenchInteractionEvent;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.tool.WrenchResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Chat feedback for {@link WrenchInteractionEvent}, gated behind {@link CommonConfig#DEV_MODE} since
 * it's debugging-facing (e.g. exposes the controller's raw state id) rather than something a regular
 * player needs to see. {@code WrenchInteractionHandler} itself never sends chat messages - this is the
 * library's own opt-in listener for that event; a mod wanting player-facing feedback regardless of dev
 * mode should add its own listener instead.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public class WrenchFeedbackHandler {

    @SubscribeEvent
    public static void onWrenchInteraction(WrenchInteractionEvent event) {
        if (!CommonConfig.DEV_MODE.get()) return;
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        switch (event.getResult()) {
            case WrenchResult.NotAMultiblock ignored -> player.sendSystemMessage(Component.literal(
                    "[MultiLib] This block isn't part of any registered multiblock."));
            case WrenchResult.AlreadyFormed already -> player.sendSystemMessage(Component.translatable(
                    "multilib.wrench.formed", already.instance().getDefinitionId().toString(),
                    resolveStateId(event.getLevel(), already.instance())));
            case WrenchResult.ModeDisallowsWrench ignored ->
                    player.sendSystemMessage(Component.translatable("multilib.wrench.mode_disallows_wrench"));
            case WrenchResult.Formed ignored ->
                    player.sendSystemMessage(Component.translatable("multilib.wrench.formed_success"));
            case WrenchResult.FormationFailed failed -> player.sendSystemMessage(Component.translatable(
                    "multilib.wrench.failed", failed.reason()));
            case WrenchResult.VariantChanged changed -> player.sendSystemMessage(Component.translatable(
                    "multilib.wrench.variant_changed", changed.definitionId().toString(),
                    changed.fromVariant(), changed.toVariant()));
        }
    }

    private static String resolveStateId(ServerLevel level, MultiblockInstance instance) {
        @Nullable BlockPos corePos = instance.getCorePos().orElse(null);
        if (corePos != null && level.getBlockEntity(corePos) instanceof AbstractMultiblockControllerBE controller) {
            return controller.getState().getId().toString();
        }
        return "unknown";
    }
}

package net.astronomy.multilib.compat.ftbquests;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.astronomy.multilib.api.event.MultiblockFormedEvent;
import net.astronomy.multilib.api.event.MultiblockStateChangedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Pushes {@link MultiblockTask} completion the instant a matching structure formation or state change
 * happens — see {@link MultiblockTask} class javadoc for why this is push-only, never polled.
 */
public final class MultiblockQuestEventListener {
    private MultiblockQuestEventListener() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(MultiblockQuestEventListener.class);
    }

    @SubscribeEvent
    public static void onFormed(MultiblockFormedEvent event) {
        // Deliberately does NOT assume IDLE here: formation only guarantees IDLE for multiblocks with a
        // real AbstractMultiblockControllerBE (which fires its own MultiblockStateChangedEvent from
        // onStructureFormed -> setState(IDLE), handled below). A JSON-only multiblock (no controller BE)
        // never reaches any tracked state at all — pushing IDLE here regardless would let a task that
        // requires "idle" complete for a structure that structurally has no state concept. Passing null
        // means "formed, no state info" — matches() only lets that satisfy tasks with no requiredState.
        event.getContext().player().ifPresent(player ->
                pushComplete(player, event.getDefinition().getId(), null));
    }

    @SubscribeEvent
    public static void onStateChanged(MultiblockStateChangedEvent event) {
        event.getContext().player().ifPresent(player ->
                pushComplete(player, event.getDefinition().getId(), event.getNewState().getId()));
    }

    private static void pushComplete(ServerPlayer player, ResourceLocation definitionId, @Nullable ResourceLocation stateId) {
        TeamData teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        for (Task task : ServerQuestFile.INSTANCE.getAllTasks()) {
            if (task instanceof MultiblockTask multiblockTask && multiblockTask.matches(definitionId, stateId)) {
                task.submitTask(teamData, player);
            }
        }
    }
}

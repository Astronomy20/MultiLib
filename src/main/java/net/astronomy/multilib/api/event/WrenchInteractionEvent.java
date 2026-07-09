package net.astronomy.multilib.api.event;

import net.astronomy.multilib.api.tool.WrenchResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Fired every time a registered wrench (see {@code IMultiblockWrench}/
 * {@code MultiLib#registerWrenchItem}) is used on a block - including when nothing happens (the
 * block isn't part of any multiblock). The library's own chat feedback for this event (see
 * {@code event.WrenchFeedbackHandler}) is gated behind {@code CommonConfig#DEV_MODE} and off by
 * default; a mod wanting player-facing feedback regardless of dev mode should listen for this event
 * itself, or use the {@code MultiblockEvents.wrench(...)} KubeJS event for scripts.
 */
public class WrenchInteractionEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    @Nullable private final ServerPlayer player;
    private final WrenchResult result;

    public WrenchInteractionEvent(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, WrenchResult result) {
        this.level = level;
        this.pos = pos;
        this.player = player;
        this.result = result;
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    @Nullable public ServerPlayer getPlayer() { return player; }
    public WrenchResult getResult() { return result; }
}

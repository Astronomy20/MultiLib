package net.astronomy.multilib.api.callback;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record MultiblockBrokenContext(MultiblockContext base, BlockPos removedPos, BreakReason reason) implements MultiblockEventContext {
    public enum BreakReason { PLAYER_BREAK, EXPLOSION, REPLACED, UNKNOWN }

    public ServerLevel level() { return base.level(); }
    public MultiblockInstance instance() { return base.instance(); }
    public MultiblockDefinition definition() { return base.definition(); }
    public BlockPos position() { return removedPos(); }
}

package net.astronomy.multilib.api.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class MultiblockBrokenEvent extends Event {
    private final MultiblockContext context;
    private final BlockPos removedPos;

    public MultiblockBrokenEvent(MultiblockContext context, BlockPos removedPos) {
        this.context = context;
        this.removedPos = removedPos;
    }

    public MultiblockContext getContext() { return context; }
    public BlockPos getRemovedPos() { return removedPos; }
    public MultiblockDefinition getDefinition() { return context.definition(); }
    public MultiblockInstance getInstance() { return context.instance(); }
    public ServerLevel getLevel() { return context.level(); }
}

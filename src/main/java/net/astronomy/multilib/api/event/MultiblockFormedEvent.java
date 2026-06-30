package net.astronomy.multilib.api.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class MultiblockFormedEvent extends Event implements ICancellableEvent {
    private final MultiblockContext context;

    public MultiblockFormedEvent(MultiblockContext context) {
        this.context = context;
    }

    public MultiblockContext getContext() { return context; }
    public MultiblockInstance getInstance() { return context.instance(); }
    public MultiblockDefinition getDefinition() { return context.definition(); }
    public ServerLevel getLevel() { return context.level(); }
}

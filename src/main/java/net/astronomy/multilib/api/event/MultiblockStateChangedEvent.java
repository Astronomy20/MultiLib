package net.astronomy.multilib.api.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.state.MultiblockState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Fired whenever a formed multiblock's controller transitions from one {@link MultiblockState} to
 * another. Unlike {@link MultiblockFormedEvent}, this is not cancellable — the state has already
 * changed by the time this event is posted, so there's nothing left to veto.
 */
public class MultiblockStateChangedEvent extends Event {
    private final MultiblockContext context;
    private final MultiblockState previousState;
    private final MultiblockState newState;

    public MultiblockStateChangedEvent(MultiblockContext context, MultiblockState previousState, MultiblockState newState) {
        this.context = context;
        this.previousState = previousState;
        this.newState = newState;
    }

    public MultiblockContext getContext() { return context; }
    public MultiblockInstance getInstance() { return context.instance(); }
    public MultiblockDefinition getDefinition() { return context.definition(); }
    public ServerLevel getLevel() { return context.level(); }
    public MultiblockState getPreviousState() { return previousState; }
    public MultiblockState getNewState() { return newState; }
}

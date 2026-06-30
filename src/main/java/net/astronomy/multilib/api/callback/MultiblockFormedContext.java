package net.astronomy.multilib.api.callback;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.server.level.ServerLevel;

public record MultiblockFormedContext(MultiblockContext base) {
    public ServerLevel level() { return base.level(); }
    public MultiblockInstance instance() { return base.instance(); }
    public MultiblockDefinition definition() { return base.definition(); }
}

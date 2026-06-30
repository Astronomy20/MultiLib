package net.astronomy.multilib.api.instance;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.server.level.ServerLevel;

public record MultiblockContext(
        ServerLevel level,
        MultiblockInstance instance,
        MultiblockDefinition definition
) {
    public static MultiblockContext of(ServerLevel level, MultiblockInstance instance) {
        MultiblockDefinition def = MultiblockRegistry.get(instance.getDefinitionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Definition not found: " + instance.getDefinitionId()));
        return new MultiblockContext(level, instance, def);
    }
}

package net.astronomy.multilib.api.instance;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

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

    /**
     * Resolves the player who formed this instance, if any and if still online. Looked up lazily
     * from {@link MultiblockInstance#getFormedBy()} via the server's player list rather than stored
     * directly on the record, since a live {@link ServerPlayer} reference would go stale across
     * save/load and shouldn't be kept around beyond the formation call that already has one in hand.
     * Never throws — returns empty if {@code formedBy} is absent or the player isn't online right now.
     */
    public Optional<ServerPlayer> player() {
        return instance.getFormedBy()
                .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid));
    }
}

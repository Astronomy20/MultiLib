package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

public interface IMultiblockPart {
    MultiblockPartComponent getMultiblockComponent();

    default boolean isPartOfStructure() {
        return getMultiblockComponent().isPartOfStructure();
    }

    default UUID getInstanceId() {
        return getMultiblockComponent().getInstanceId();
    }

    default Optional<MultiblockInstance> getInstance(ServerLevel level) {
        return getMultiblockComponent().getInstance(level);
    }

    default Optional<AbstractMultiblockControllerBE> getController(ServerLevel level) {
        return getMultiblockComponent().getController(level);
    }

    default void onJoinedStructure(MultiblockInstance instance) {}

    default void onLeftStructure() {}
}

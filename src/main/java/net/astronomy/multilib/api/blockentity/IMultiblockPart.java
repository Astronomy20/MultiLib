package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.api.ability.MultiblockAbility;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IMultiblockPart {
    MultiblockPartComponent getMultiblockComponent();

    /**
     * The role(s) this part fulfills within the structure once formed (e.g. an item/energy port),
     * looked up via {@link net.astronomy.multilib.api.ability.MultiblockAbilities} by whatever code
     * drives the controller's logic. Unlike a fixed 1:1 "this symbol is the IO port", a structure can
     * declare any number of positions with the same ability - the controller just asks for all parts
     * that provide it. Empty by default: most parts (plain structural blocks) provide no ability.
     */
    default Set<MultiblockAbility<?>> getAbilities() {
        return Set.of();
    }

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

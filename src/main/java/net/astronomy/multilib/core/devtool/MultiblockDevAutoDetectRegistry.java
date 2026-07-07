package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry of every dev-block that currently has auto-detect switched on, across every loaded
 * dimension - {@code MultiblockTickHandler} iterates this every ~20 ticks to re-scan them.
 * <p>
 * Deliberately independent of {@link MultiblockDevListSessionRegistry}: auto-detect on/off and the HUD
 * list's visibility are separate toggles (see the GUI's two buttons) - a dev-block should keep re-scanning
 * in the background (keeping {@code lastScan}/the tag glow fresh) even while nobody's list is currently
 * pointed at it, not just while someone happens to be watching. Previously the tick handler only ever
 * rescanned whichever block a player's list session pointed at, which meant auto-detect alone (without
 * ever showing the list) did nothing at all - looking like it needed to be toggled off and back on to
 * "kick" it, when really nothing was driving it continuously in the first place.
 */
public final class MultiblockDevAutoDetectRegistry {

    private MultiblockDevAutoDetectRegistry() {
    }

    public record Key(ResourceKey<Level> dimension, BlockPos devBlockPos) {
    }

    private static final Set<Key> ACTIVE = ConcurrentHashMap.newKeySet();

    public static void register(ResourceKey<Level> dimension, BlockPos devBlockPos) {
        ACTIVE.add(new Key(dimension, devBlockPos));
    }

    public static void unregister(ResourceKey<Level> dimension, BlockPos devBlockPos) {
        ACTIVE.remove(new Key(dimension, devBlockPos));
    }

    public static Set<Key> getAll() {
        return ACTIVE;
    }
}

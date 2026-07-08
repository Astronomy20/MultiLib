package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global index of every currently-loaded dev-block, across every dimension. Unlike
 * {@link MultiblockDevAutoDetectRegistry} (which only tracks blocks with auto-detect switched on),
 * this tracks all of them, so world-event handlers ({@code MultiblockDevBreakHandler}) can find
 * which dev-block's scanned area an event landed in without iterating loaded chunks.
 * <p>
 * Registered from the block entity's {@code onLoad}, unregistered from {@code setRemoved} - which
 * covers both "block broken" and "chunk unloaded". Unload-time removal never hides a relevant
 * entry: a block break can only happen in a loaded chunk, and loading the chunk re-registers via
 * {@code onLoad}. Entries are plain dimension+position keys (no {@code Level}/BE references), so
 * nothing world-bound leaks across world reloads; consumers self-heal stale keys by checking the
 * block entity still exists, same as {@code MultiblockTickHandler} does for the auto-detect registry.
 */
public final class MultiblockDevBlockIndex {

    private MultiblockDevBlockIndex() {
    }

    public record Key(ResourceKey<Level> dimension, BlockPos devBlockPos) {
    }

    private static final Set<Key> LOADED = ConcurrentHashMap.newKeySet();

    public static void register(ResourceKey<Level> dimension, BlockPos devBlockPos) {
        LOADED.add(new Key(dimension, devBlockPos));
    }

    public static void unregister(ResourceKey<Level> dimension, BlockPos devBlockPos) {
        LOADED.remove(new Key(dimension, devBlockPos));
    }

    public static Set<Key> getAll() {
        return LOADED;
    }
}

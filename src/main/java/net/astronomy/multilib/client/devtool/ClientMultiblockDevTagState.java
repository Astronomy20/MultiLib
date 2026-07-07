package net.astronomy.multilib.client.devtool;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of "which position is currently tagged as core/activation" for each Multiblock
 * Dev Block the player knows about, populated by {@code MultiblockDevBlockEntity}'s client sync (that
 * class, owned by another task in parallel, calls {@link #update} whenever its client-side copy
 * receives a tag state). {@link MultiblockDevGlowRenderer} reads {@link #getAll()} every frame instead
 * of scanning loaded chunks/block entities itself - in typical usage this map holds at most one entry.
 * <p>
 * Backed by a {@link ConcurrentHashMap} even though all real access is expected to happen on the
 * client/render thread: this costs nothing meaningful at this scale (0-1 entries) and removes any need
 * to reason about whether a stray call (e.g. from network packet handling before it's marshalled onto
 * the render thread) could race with a render pass.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMultiblockDevTagState {

    private ClientMultiblockDevTagState() {
    }

    /**
     * @param positions every position within the dev-block's area currently holding the tagged block
     *                  type - all of them get outlined, not just the one originally clicked, so a
     *                  duplicated block type reads as "here's everywhere this shows up" at a glance.
     * @param isCore    false means "activation" (the fallback saved when the block type wasn't unique).
     * @param blockName display name of the tagged block, for the floating label the renderer draws.
     */
    public record TagInfo(List<BlockPos> positions, boolean isCore, String blockName) {
    }

    private static final Map<BlockPos, TagInfo> TAGS = new ConcurrentHashMap<>();

    /**
     * @param devBlockPos position of the dev-block that owns this tag (map key).
     * @param info        the current tag, or {@code null} to remove/clear it (e.g. the dev-block was
     *                    broken, or its tag was reset).
     */
    public static void update(BlockPos devBlockPos, TagInfo info) {
        if (info == null) {
            TAGS.remove(devBlockPos);
        } else {
            TAGS.put(devBlockPos, info);
        }
    }

    public static Collection<TagInfo> getAll() {
        return TAGS.values();
    }
}

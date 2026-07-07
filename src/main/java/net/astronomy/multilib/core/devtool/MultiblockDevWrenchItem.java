package net.astronomy.multilib.core.devtool;

import net.minecraft.world.item.Item;

/**
 * Dev-only tool: right-clicking a block with this item held is the sole trigger for core/activation
 * tagging (see {@code MultiblockDevTagHandler}). Replaces the earlier sneak+right-click-with-any-item
 * gesture, which turned out to be unreliable in practice - shift-clicking a block that also had its own
 * use behavior (most commonly the dev block itself, when its scanned area includes its own position)
 * could tag and then immediately untag the same click. Requiring this specific item removes the ambiguity
 * entirely: holding it suppresses every other interaction on the clicked block (GUI opens, item use,
 * placement, etc.), so a wrench click is never anything other than a tag attempt.
 * <p>
 * Marker-only class, same as most tool items - all behavior lives in the global listener, not here, so
 * tagging keeps working uniformly regardless of what's being right-clicked.
 * <p>
 * Only ever registered when {@code CommonConfig.DEV_MODE} is true - see {@link MultiblockDevRegistry}.
 */
public class MultiblockDevWrenchItem extends Item {
    public MultiblockDevWrenchItem(Properties properties) {
        super(properties);
    }
}

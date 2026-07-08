package net.astronomy.multilib.core.preference;

import net.minecraft.world.item.Item;

/**
 * Dev-only tool: right-clicking a core/activation block with this item held opens a picker of every
 * definition that block is ambiguously a candidate for (see {@code MultiblockAmbiguityResolver}),
 * letting a developer bind one of them to that exact position via {@code MultiLibAPI#setPreferredDefinition}
 * - see {@code client.preference.MultiblockPreferenceInputHandler} for the client-side trigger and
 * {@code MultiblockPreferenceScreen} for the picker itself. A no-op click (no menu opens) when the
 * clicked block isn't actually ambiguous for anything.
 * <p>
 * Marker-only class, same as {@code MultiblockDevWrenchItem} - all behavior lives in the global input
 * listener, not here. Only ever registered when {@code CommonConfig.DEV_MODE} is true - see
 * {@link MultiblockPreferenceToolRegistry}.
 */
public class MultiblockPreferenceWrenchItem extends Item {
    public MultiblockPreferenceWrenchItem(Properties properties) {
        super(properties);
    }
}

package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoped builder for one {@link MultiblockBuilder#variant(String, java.util.function.Consumer)} block:
 * that variant's own layers, plus any key overrides local to it.
 * <p>
 * Geometry + keys is deliberately the whole v1 surface (F12 step A) - everything else a definition can
 * express (callbacks, formation mode, rotation config, tiers, validator, ...) is shared across every
 * variant of a definition and stays on {@link MultiblockBuilder} itself, not here.
 */
public final class VariantBuilder {
    private final List<List<String>> layers = new ArrayList<>();
    private final Map<Character, BlockIngredient> keys = new LinkedHashMap<>();

    VariantBuilder() {}

    /**
     * Adds one horizontal (Y) slice of this variant's structure - same semantics as
     * {@link MultiblockBuilder#layer(String...)}, scoped to this variant only.
     */
    public VariantBuilder layer(String... rows) {
        this.layers.add(Arrays.asList(rows));
        return this;
    }

    /**
     * Variant-local key: overrides the shared top-level {@link MultiblockBuilder#key(char, BlockIngredient)}
     * declaration for {@code symbol} within this variant only. A variant that doesn't override a symbol
     * still sees the shared declaration at build time.
     */
    public VariantBuilder key(char symbol, BlockIngredient ingredient) {
        this.keys.put(symbol, ingredient);
        return this;
    }

    public VariantBuilder key(char symbol, Block block) {
        return key(symbol, BlockIngredient.of(block));
    }

    /** String overload for scripting - see {@link MultiblockBuilder#key(char, String)}. */
    public VariantBuilder key(char symbol, String blockOrTagId) {
        return key(symbol, BlockIngredient.parse(blockOrTagId));
    }

    /** Package-private: only {@link MultiblockBuilder#variant(String, java.util.function.Consumer)} calls this, after the caller's config lambda has populated this builder. */
    VariantSpec toSpec(String name) {
        return new VariantSpec(name, layers, keys);
    }
}

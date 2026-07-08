package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.api.ingredient.BlockIngredient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One named geometry variant as declared via {@link MultiblockBuilder#variant(String, java.util.function.Consumer)}:
 * its own layers, plus any variant-local key overrides declared inside that {@code variant(...)} block.
 * <p>
 * Stored verbatim on the parent {@link MultiblockDefinition} (see
 * {@link MultiblockDefinition#getRawVariantSpecs()}) purely so {@link MultiblockDefinition#toBuilder()}
 * can re-emit the original declarations losslessly (KubeJS's {@code MultiblockEvents.modify} depends on
 * this round-trip). The actually-used geometry - already merged with the shared top-level keys - lives
 * on the corresponding {@link MultiblockDefinition} instead: the parent itself for the first variant, or
 * one of {@link MultiblockDefinition#getVariantDefinitions()} for every other one.
 */
public record VariantSpec(String name, List<List<String>> layers, Map<Character, BlockIngredient> keys) {

    public VariantSpec {
        layers = layers.stream().map(List::copyOf).collect(Collectors.toUnmodifiableList());
        keys = Map.copyOf(keys);
    }
}

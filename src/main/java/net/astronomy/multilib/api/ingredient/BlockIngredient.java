package net.astronomy.multilib.api.ingredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

public interface BlockIngredient {
    boolean matches(BlockState state);
    Set<Block> getCandidateBlocks();

    /**
     * The {@link BlockState} previews (JEI/REI/EMI 3D model, ghost overlay) should render for this
     * ingredient. Defaults to the first candidate block's default state — blocks with meaningful
     * facing/property variants (furnaces, droppers, etc.) should override this to force a specific
     * orientation, e.g. via {@link StatePropertyIngredient}, which already applies any properties
     * required by {@code .require(...)}.
     */
    @Nullable
    default BlockState getRenderState() {
        Set<Block> candidates = getCandidateBlocks();
        if (candidates.isEmpty()) return null;
        return candidates.iterator().next().defaultBlockState();
    }

    static BlockIngredient of(Block block) {
        return new SingleBlockIngredient(block);
    }

    static StatePropertyIngredient.Builder ofState(Block block) {
        return StatePropertyIngredient.forBlock(block);
    }

    static BlockIngredient tag(TagKey<Block> tag) {
        return new TagBlockIngredient(tag);
    }

    static BlockIngredient anyOf(BlockIngredient... ingredients) {
        return new AnyOfBlockIngredient(Arrays.asList(ingredients));
    }

    static BlockIngredient predicate(Predicate<BlockState> predicate) {
        return new PredicateBlockIngredient(predicate);
    }

    static BlockIngredient any() {
        return AnyBlockIngredient.INSTANCE;
    }
}

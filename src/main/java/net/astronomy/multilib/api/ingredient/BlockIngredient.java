package net.astronomy.multilib.api.ingredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

public interface BlockIngredient {
    boolean matches(BlockState state);
    Set<Block> getCandidateBlocks();

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

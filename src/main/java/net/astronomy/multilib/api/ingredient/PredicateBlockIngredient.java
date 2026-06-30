package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Predicate;

class PredicateBlockIngredient implements BlockIngredient {
    private final Predicate<BlockState> predicate;

    PredicateBlockIngredient(Predicate<BlockState> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean matches(BlockState state) {
        return predicate.test(state);
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        return Set.of();
    }
}

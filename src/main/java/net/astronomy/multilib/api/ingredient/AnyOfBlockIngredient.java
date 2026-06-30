package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AnyOfBlockIngredient implements BlockIngredient {
    private final List<BlockIngredient> ingredients;

    AnyOfBlockIngredient(List<BlockIngredient> ingredients) {
        this.ingredients = List.copyOf(ingredients);
    }

    @Override
    public boolean matches(BlockState state) {
        for (BlockIngredient ingredient : ingredients) {
            if (ingredient.matches(state)) return true;
        }
        return false;
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        Set<Block> result = new HashSet<>();
        for (BlockIngredient ingredient : ingredients) {
            result.addAll(ingredient.getCandidateBlocks());
        }
        return result;
    }
}

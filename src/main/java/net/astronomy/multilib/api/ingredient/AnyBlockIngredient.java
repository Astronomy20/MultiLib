package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

class AnyBlockIngredient implements BlockIngredient {
    static final AnyBlockIngredient INSTANCE = new AnyBlockIngredient();

    private AnyBlockIngredient() {}

    @Override
    public boolean matches(BlockState state) {
        return true;
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        return Set.of();
    }
}

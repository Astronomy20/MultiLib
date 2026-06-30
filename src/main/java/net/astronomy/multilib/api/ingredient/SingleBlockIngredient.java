package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

class SingleBlockIngredient implements BlockIngredient {
    private final Block block;

    SingleBlockIngredient(Block block) {
        this.block = block;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(block);
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        return Set.of(block);
    }
}

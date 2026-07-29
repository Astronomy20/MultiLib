package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
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

    /**
     * Value equality (not just reference) so a {@code PatternProvider} built with its own
     * {@code BlockIngredient.of(...)} call still resolves to the same tier symbol as a separately
     * constructed but equivalent one registered via {@code .key(...)} - see
     * {@link net.astronomy.multilib.core.matching.FunctionalMatcher#collectMatchData}.
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof SingleBlockIngredient other && block == other.block;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(block);
    }
}

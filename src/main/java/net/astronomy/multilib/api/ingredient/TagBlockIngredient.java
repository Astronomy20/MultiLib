package net.astronomy.multilib.api.ingredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

class TagBlockIngredient implements BlockIngredient {
    private final TagKey<Block> tagKey;

    TagBlockIngredient(TagKey<Block> tagKey) {
        this.tagKey = tagKey;
    }

    @Override
    public boolean matches(BlockState state) {
        return state.is(tagKey);
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        return Set.of();
    }
}

package net.astronomy.multilib.api.ingredient;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StatePropertyIngredient implements BlockIngredient {
    private final Block block;
    private final Map<Property<?>, Comparable<?>> requiredProperties;

    private StatePropertyIngredient(Block block, Map<Property<?>, Comparable<?>> requiredProperties) {
        this.block = block;
        this.requiredProperties = Map.copyOf(requiredProperties);
    }

    public static Builder forBlock(Block block) {
        return new Builder(block);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean matches(BlockState state) {
        if (!state.is(block)) return false;
        for (var entry : requiredProperties.entrySet()) {
            Property prop = entry.getKey();
            if (!state.getValue(prop).equals(entry.getValue())) return false;
        }
        return true;
    }

    @Override
    public Set<Block> getCandidateBlocks() {
        return Set.of(block);
    }

    /**
     * Applies every required property (e.g. a forced FACING) on top of the block's default state, so
     * previews/ghost overlays render directional blocks (furnaces, droppers, etc.) in the orientation
     * this ingredient actually demands, instead of always falling back to the block's default facing.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockState getRenderState() {
        BlockState state = block.defaultBlockState();
        for (var entry : requiredProperties.entrySet()) {
            Property prop = entry.getKey();
            Comparable value = entry.getValue();
            if (state.hasProperty(prop)) {
                state = state.setValue(prop, value);
            }
        }
        return state;
    }

    public static final class Builder {
        private final Block block;
        private final Map<Property<?>, Comparable<?>> properties = new LinkedHashMap<>();

        private Builder(Block block) {
            this.block = block;
        }

        public <T extends Comparable<T>> Builder require(Property<T> property, T value) {
            properties.put(property, value);
            return this;
        }

        public StatePropertyIngredient build() {
            return new StatePropertyIngredient(block, properties);
        }
    }
}

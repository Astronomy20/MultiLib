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

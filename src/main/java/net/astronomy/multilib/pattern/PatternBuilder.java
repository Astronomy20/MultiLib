package net.astronomy.multilib.pattern;

import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Builds a PatternManager safely. Supports automatic registration
 */
public class PatternBuilder {

    private final Map<Character, Block> blockMap = new HashMap<>();
    private final List<List<String>> layers = new ArrayList<>();
    private PatternAction action;
    private boolean allowHorizontalRotation = true;
    private boolean allowVerticalRotation = false;
    private boolean allowSideRotation = false;
    private boolean allowUpsideDown = false;

    public PatternBuilder() {}

    public PatternBuilder key(char symbol, Block block) {
        blockMap.put(symbol, block);
        return this;
    }

    public PatternBuilder layer(String... rows) {
        layers.add(Arrays.asList(rows));
        return this;
    }

    public PatternBuilder action(PatternAction action) {
        this.action = action;
        return this;
    }

    public PatternBuilder allowHorizontalRotation(boolean value) {
        this.allowHorizontalRotation = value;
        return this;
    }

    public PatternBuilder allowVerticalRotation(boolean value) {
        this.allowVerticalRotation = value;
        return this;
    }

    public PatternBuilder allowSideRotation(boolean value) {
        this.allowSideRotation = value;
        return this;
    }

    public PatternBuilder allowUpsideDown(boolean value) {
        this.allowUpsideDown = value;
        return this;
    }

    public PatternManager build() {
        if (layers.isEmpty()) {
            throw new IllegalStateException("Pattern must have at least one layer!");
        }

        if ((allowSideRotation || allowUpsideDown) && !allowVerticalRotation) {
            throw new IllegalStateException("Side rotation and upside-down rotation require vertical rotation to be enabled!");
        }

        PatternManager pattern = new PatternManager(
                blockMap,
                layers,
                action,
                allowHorizontalRotation,
                allowVerticalRotation,
                allowSideRotation,
                allowUpsideDown
        );

        if (action != null) {
            PatternRegistry.register(pattern, action);
        }

        return pattern;
    }
}
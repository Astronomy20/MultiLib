package net.astronomy.multilib.pattern;

import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Builds a PatternManager safely. Supports automatic registration
 */
public class PatternBuilder {

    private final Map<Character, Block> blockMap = new HashMap<>();
    private final List<List<String>> layers = new ArrayList<>();
    private final boolean is3D;
    private PatternAction action;
    private boolean allowMirror = false;
    private boolean allowVerticalRotation = false;

    public PatternBuilder(boolean is3D) {
        this.is3D = is3D;
    }

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

    public PatternBuilder allowVerticalRotation(boolean value) {
        this.allowVerticalRotation = value;
        return this;
    }

    public PatternManager build() {
        if (layers.isEmpty()) {
            throw new IllegalStateException("Pattern must have at least one layer!");
        }

        PatternManager pattern = new PatternManager(blockMap, layers, is3D, action, allowMirror, allowVerticalRotation);

        if (action != null) {
            PatternRegistry.register(pattern, action);
        }

        return pattern;
    }
}
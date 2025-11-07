package net.astronomy.multilib.pattern;

import net.minecraft.world.level.block.Block;
import java.util.*;

/**
 * Represents a 2D or 3D pattern with optional action.
 */
public class PatternManager {

    private final Map<Character, Block> blockMap;
    private final List<List<String>> layers;
    private final boolean is3D;
    private final PatternAction action;

    PatternManager(Map<Character, Block> blockMap, List<List<String>> layers, boolean is3D, PatternAction action) {
        this.blockMap = Map.copyOf(blockMap);
        this.layers = List.copyOf(layers);
        this.is3D = is3D;
        this.action = action;
    }

    public Map<Character, Block> getBlockMap() {
        return blockMap;
    }

    public List<List<String>> getLayers() {
        return layers;
    }

    public boolean is3D() {
        return is3D;
    }

    public PatternAction getAction() {
        return action;
    }

    public int getLayerCount() {
        return is3D ? layers.size() : 1;
    }

    public boolean isKeyBlock(Block block) {
        return blockMap.containsValue(block);
    }

    public Set<Block> getKeyBlocks() {
        return Set.copyOf(blockMap.values());
    }

    public static PatternBuilder pattern2D() { return new PatternBuilder(false); }
    public static PatternBuilder pattern3D() { return new PatternBuilder(true); }
}
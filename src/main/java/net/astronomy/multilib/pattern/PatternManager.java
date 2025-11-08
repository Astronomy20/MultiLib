package net.astronomy.multilib.pattern;

import net.minecraft.world.level.block.Block;
import java.util.*;

public class PatternManager {

    private final Map<Character, Block> blockMap;
    private final List<List<String>> layers;
    private final PatternAction action;
    private final boolean allowsHorizontalRotation;
    private final boolean allowVerticalRotation;

    PatternManager(Map<Character, Block> blockMap, List<List<String>> layers, PatternAction action, boolean allowsHorizontalRotation,boolean allowVerticalRotation) {
        this.blockMap = Map.copyOf(blockMap);
        this.layers = List.copyOf(layers);
        this.action = action;
        this.allowsHorizontalRotation = allowsHorizontalRotation;
        this.allowVerticalRotation = allowVerticalRotation;
    }

    public Map<Character, Block> getBlockMap() {
        return blockMap;
    }

    public List<List<String>> getLayers() {
        return layers;
    }

    public PatternAction getAction() {
        return action;
    }

    public int getLayerCount() {
        return layers.size();
    }

    public boolean isKeyBlock(Block block) {
        return blockMap.containsValue(block);
    }

    public Set<Block> getKeyBlocks() {
        return Set.copyOf(blockMap.values());
    }

    public boolean allowsHorizontalRotation() {
        return allowsHorizontalRotation;
    }

    public boolean allowsVerticalRotation() {
        return allowVerticalRotation;
    }

    public static PatternBuilder pattern() {
        return new PatternBuilder();
    }
}